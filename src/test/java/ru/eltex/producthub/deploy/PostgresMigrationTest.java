package ru.eltex.producthub.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Миграции Flyway на настоящем PostgreSQL (SPEC.md этап 7). Нужен Docker; без него тест пропускается.
 * Отдельно проверяет миграцию V14, которая перераспределяет владельцев уже существующих SKU.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).target(target).load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void allMigrationsApplyOnEmptyPostgres() throws SQLException {
        Flyway f = flyway("latest");
        f.clean();
        var result = f.migrate();
        assertTrue(result.success);
        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertEquals(16, count(st, "select count(*) from app_user"), "тестовые пользователи, владельцы и коллеги");
            assertEquals(7, count(st, "select count(*) from information_schema.tables where table_name in"
                    + " ('sku_owner', 'feedback_comment', 'view_log', 'search_log', 'task', 'question', 'firmware')"));
        }
    }

    @Test
    void ownersMigrationDistributesExistingSkusByFamily() throws SQLException {
        Flyway before = flyway("13");
        before.clean();
        before.migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            // линейки из V3: WEP, WOP (Wi-Fi), LTP (PON); добавляем SKU в трех линейках
            st.execute("insert into product_line (code, name, family, owner_id, sort_order)"
                    + " select 'VOIPX', 'Тест VoIP', 'VoIP', id, 900 from app_user where login = 'kuznetsov'");
            for (String[] row : new String[][] {{"T-WEP", "WEP"}, {"T-WOP", "WOP"}, {"T-LTP", "LTP"}, {"T-VOIP", "VOIPX"}}) {
                st.execute("insert into sku (model, model_normalized, line_id, category, owner_id, lifecycle_status, contour,"
                        + " created_at, updated_at) select '" + row[0] + "', '" + row[0] + "', l.id, 'Тест', u.id, 'PRODUCTION', 'INTERNAL',"
                        + " now(), now() from product_line l, app_user u where l.code = '" + row[1] + "' and u.login = 'kuznetsov'");
            }
        }
        var result = flyway("latest").migrate();
        assertTrue(result.success);
        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertEquals(2, owners(st, "T-WEP"), "Wi-Fi: Кузнецов и Смит");
            assertEquals(2, owners(st, "T-LTP"), "PON: Полухин и Литош");
            assertEquals(1, owners(st, "T-VOIP"), "VoIP: Голованов");
            assertEquals("kuznetsov", primary(st, "T-WEP"), "первая линейка семейства - первому в списке");
            assertEquals("smith", primary(st, "T-WOP"), "вторая линейка - второму");
            assertEquals("golovanov", primary(st, "T-VOIP"));
            assertEquals(0, count(st, "select count(*) from sku s where not exists (select 1 from sku_owner so"
                    + " where so.sku_id = s.id and so.user_id = s.owner_id)"), "основной владелец всегда среди владельцев");
        }
    }

    private static long owners(Statement st, String model) throws SQLException {
        return count(st, "select count(*) from sku_owner so join sku s on s.id = so.sku_id where s.model = '" + model + "'");
    }

    private static String primary(Statement st, String model) throws SQLException {
        try (ResultSet rs = st.executeQuery("select u.login from sku s join app_user u on u.id = s.owner_id where s.model = '" + model + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
