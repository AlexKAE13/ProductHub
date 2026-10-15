package ru.eltex.producthub.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.eltex.producthub.dashboard.service.DashboardService;
import ru.eltex.producthub.feedback.service.CommentInput;
import ru.eltex.producthub.feedback.service.FeedbackService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.search.service.SearchIndexer;
import ru.eltex.producthub.search.service.SearchService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.service.CatalogFilter;
import ru.eltex.producthub.sku.service.CatalogService;

/**
 * Все приложение целиком на PostgreSQL: схема совпадает с сущностями (ddl-auto=validate), импорт из seed, каталог, поиск,
 * дашборд и комментарии работают на SQL, который в тестах обычно проверяется только на H2.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PostgresApplicationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    SeedImportService importer;
    @Autowired
    CatalogService catalog;
    @Autowired
    SearchIndexer indexer;
    @Autowired
    SearchService search;
    @Autowired
    DashboardService dashboard;
    @Autowired
    FeedbackService feedback;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void wholeApplicationWorksOnPostgres() {
        importer.execute("test");
        HubUserDetails product = users.loadUserByUsername("product");
        HubUserDetails head = users.loadUserByUsername("head");

        var page = catalog.search(new CatalogFilter(null, null, null, null, null, null, false, true, null, "model", "asc", 0), product);
        assertTrue(page.total() > 300, "каталог");
        assertFalse(page.items().isEmpty());

        assertTrue(indexer.reindexAll() > 300, "индекс строится из PostgreSQL");
        assertEquals("WEP-550K", search.suggest("wep550k", true).groups().get(0).hits().get(0).title());

        Long kuznetsov = users.loadUserByUsername("kuznetsov").getId();
        assertTrue(dashboard.build(null).skuTotal() > 300, "дашборд по всем");
        assertTrue(dashboard.build(kuznetsov).skuTotal() > 0, "дашборд по владельцу");

        var comment = feedback.create(new CommentInput("/catalog", "", "Каталог", "/catalog", "catalog/index", "main > h1", "h1", "Каталог",
                null, null, null, "1x1", "BUG", "Проверка на PostgreSQL", null), product);
        assertTrue(feedback.exportMarkdown(null, "/catalog", null, product).contains("Проверка на PostgreSQL"));
        assertEquals(1, feedback.forPage("/catalog", head).stream().filter(c -> c.id() == comment.id()).count());

        List<String> owners = jdbc.queryForList("select u.login from sku_owner so join sku s on s.id = so.sku_id"
                + " join app_user u on u.id = so.user_id where s.model = 'WEP-550K'", String.class);
        assertEquals(2, owners.size(), "совладельцы после импорта");
    }
}
