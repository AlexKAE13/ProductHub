package ru.eltex.producthub.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.eltex.producthub.transfer.H2DataTransfer;

/** Перенос пилотных данных из H2 в PostgreSQL: строки, связи на себя и по кругу, счетчики id. Нужен Docker. */
@Testcontainers(disabledWithoutDocker = true)
class H2ToPostgresTransferTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @TempDir
    Path dir;

    private static long one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void copiesRowsAndRestoresSelfAndCircularLinks() throws Exception {
        String h2 = "jdbc:h2:file:" + dir.resolve("pilot") + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        Flyway.configure().dataSource(h2, "sa", "").locations("classpath:db/migration").load().migrate();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        long users;
        try (Connection c = DriverManager.getConnection(h2, "sa", ""); Statement st = c.createStatement()) {
            // SKU с преемником (связь на себя), комментарий с ответом, материал с текущей версией (цикл), сообщение журнала
            st.execute("insert into sku (model, model_normalized, line_id, category, owner_id, lifecycle_status, contour, created_at, updated_at)"
                    + " select 'P-OLD', 'POLD', l.id, 'Тест', u.id, 'EOL', 'INTERNAL', now(), now() from product_line l, app_user u"
                    + " where l.code = 'WEP' and u.login = 'kuznetsov'");
            st.execute("insert into sku (model, model_normalized, line_id, category, owner_id, lifecycle_status, contour, successor_id,"
                    + " created_at, updated_at) select 'P-NEW', 'PNEW', l.id, 'Тест', u.id, 'PRODUCTION', 'INTERNAL',"
                    + " (select id from sku where model = 'P-OLD'), now(), now() from product_line l, app_user u"
                    + " where l.code = 'WEP' and u.login = 'kuznetsov'");
            st.execute("insert into feedback_comment (page_path, kind, status, body, author_login, author_name, created_at, updated_at)"
                    + " values ('/catalog', 'BUG', 'NEW', 'Корневой', 'kuznetsov', 'Александр Кузнецов', now(), now())");
            st.execute("insert into feedback_comment (parent_id, page_path, kind, status, body, author_login, author_name, created_at, updated_at)"
                    + " select id, '/catalog', 'IDEA', 'NEW', 'Ответ', 'smith', 'Александр Смит', now(), now() from feedback_comment");
            st.execute("insert into artifact_type (name) values ('Тип переноса')");
            st.execute("insert into artifact (title, type_id, language, status, owner_id, contour, last_reviewed_at, review_period_days,"
                    + " review_due_at, created_by, created_at, updated_at) select 'Материал', t.id, 'RU', 'ACTUAL', u.id, 'INTERNAL', now(), 90,"
                    + " now(), 'test', now(), now() from artifact_type t, app_user u where t.name = 'Тип переноса' and u.login = 'kuznetsov'");
            st.execute("insert into artifact_version (artifact_id, version_no, uploaded_by, uploaded_at) select id, 1, 'test', now() from artifact");
            st.execute("update artifact set current_version_id = (select id from artifact_version)");
            st.execute("insert into search_log (kind, user_login, query_text, at) values ('PAGE', 'kuznetsov', 'запрос', now())");
            users = one(c, "select count(*) from app_user");
        }

        Map<String, Integer> counts;
        try (Connection src = DriverManager.getConnection(h2, "sa", "");
             Connection dst = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            counts = new H2DataTransfer().copy(src, dst);
        }

        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            assertEquals(users, one(c, "select count(*) from app_user"));
            assertEquals(2, one(c, "select count(*) from sku"));
            assertEquals(2, one(c, "select count(*) from feedback_comment"));
            assertEquals(1, one(c, "select count(*) from search_log"));
            assertTrue(counts.get("sku") == 2 && counts.get("feedback_comment") == 2);
            // связи на себя и по кругу восстановлены
            assertEquals(1, one(c, "select count(*) from sku n join sku o on o.id = n.successor_id where n.model = 'P-NEW' and o.model = 'P-OLD'"));
            assertEquals(1, one(c, "select count(*) from feedback_comment r join feedback_comment p on p.id = r.parent_id where r.body = 'Ответ'"));
            assertEquals(1, one(c, "select count(*) from artifact a join artifact_version v on v.id = a.current_version_id"));
            // счетчики id продолжаются после максимума
            st.execute("insert into feedback_comment (page_path, kind, status, body, author_login, author_name, created_at, updated_at)"
                    + " values ('/x', 'BUG', 'NEW', 'Новый после переноса', 'smith', 'Смит', now(), now())");
            assertNotNull(one(c, "select max(id) from feedback_comment"));
            assertEquals(3, one(c, "select count(*) from feedback_comment"));
            // служебная история миграций не тронута
            assertEquals(14, one(c, "select count(*) from flyway_schema_history"));
        }
    }

    @Test
    void refusesWhenSchemaVersionsDiffer() throws Exception {
        String h2 = "jdbc:h2:file:" + dir.resolve("old") + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(h2, "sa", "").locations("classpath:db/migration").target("13").load().migrate();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection src = DriverManager.getConnection(h2, "sa", "");
             Connection dst = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> new H2DataTransfer().copy(src, dst));
            assertTrue(e.getMessage().contains("Версии схемы различаются"));
        }
    }
}
