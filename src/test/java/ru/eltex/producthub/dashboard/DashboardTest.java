package ru.eltex.producthub.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.file;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.pdf;
import static ru.eltex.producthub.artifact.ArtifactTestSupport.unique;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.common.ObjectViewed;
import ru.eltex.producthub.dashboard.service.Dashboard;
import ru.eltex.producthub.dashboard.service.DashboardService;
import ru.eltex.producthub.dashboard.service.ViewLogService;
import ru.eltex.producthub.importer.SeedImportService;
import ru.eltex.producthub.qa.domain.QuestionKind;
import ru.eltex.producthub.qa.domain.TargetType;
import ru.eltex.producthub.qa.service.QuestionService;
import ru.eltex.producthub.search.service.SearchLogService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.HubUserDetailsService;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.SkuRepository;

/** Критерий этапа 6: дашборд показывает реальные цифры, PRODUCT видит только свои. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class DashboardTest {

    @Autowired
    DashboardService dashboard;
    @Autowired
    ViewLogService viewLog;
    @Autowired
    SearchLogService searchLog;
    @Autowired
    ArtifactService artifacts;
    @Autowired
    ArtifactTypeService types;
    @Autowired
    QuestionService questions;
    @Autowired
    SkuRepository skus;
    @Autowired
    SeedImportService importer;
    @Autowired
    HubUserDetailsService users;
    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    JdbcTemplate jdbc;

    HubUserDetails product;
    HubUserDetails head;
    HubUserDetails sales;
    HubUserDetails admin;
    Sku sku;
    Long ownerId;

    @BeforeEach
    void setUp() {
        importer.execute("test");
        product = users.loadUserByUsername("product");
        head = users.loadUserByUsername("head");
        sales = users.loadUserByUsername("sales");
        admin = users.loadUserByUsername("admin");
        sku = skus.findByModelNormalized("WEP550K").orElseThrow();
        ownerId = sku.getOwner().getId();
    }

    private Artifact artifact(String title, Language language) {
        ArtifactDraft d = new ArtifactDraft(title, null, types.byName("Даташит").orElseThrow(), language, ArtifactStatus.ACTUAL,
                sku.getOwner(), null, null, null, List.of(), List.of(), null);
        return artifacts.create(d, List.of(file(title + ".pdf", pdf())), null, product);
    }

    // ----------------------------------------------------------- права и охват

    @Test
    void productAlwaysSeesOwnNumbersHeadPicksAnyone() {
        assertEquals(product.getId(), dashboard.scopeFor(product, ownerId), "PRODUCT не может смотреть чужие цифры");
        assertEquals(product.getId(), dashboard.scopeFor(product, null));
        assertEquals(ownerId, dashboard.scopeFor(head, ownerId));
        assertNull(dashboard.scopeFor(head, null), "руководитель без выбора видит всех");
        assertNull(dashboard.scopeFor(admin, null));
        assertTrue(dashboard.canView(product) && dashboard.canView(head) && dashboard.canView(admin));
        assertFalse(dashboard.canView(sales), "менеджеры по продажам дашборд не видят");
    }

    @Test
    void skuNumbersFollowScope() {
        Dashboard all = dashboard.build(null);
        Dashboard own = dashboard.build(ownerId);
        Dashboard other = dashboard.build(product.getId());
        assertTrue(all.skuTotal() > 300, "в каталоге больше 300 SKU");
        assertEquals(all.skuTotal(), all.skuByStatus().stream().mapToLong(Dashboard.Count::n).sum());
        assertTrue(own.skuTotal() > 0 && own.skuTotal() <= all.skuTotal());
        assertTrue(dashboard.build(otherOwner()).skuTotal() <= all.skuTotal(), "охват владельца не больше общего");
        assertEquals(0, other.skuTotal(), "у тестового продакта своих SKU нет");
        assertEquals(10, all.weakestSkus().size());
        assertTrue(all.weakestSkus().get(0).n() <= all.weakestSkus().get(9).n(), "от самых пустых к более полным");
        assertTrue(all.avgCompleteness() > 0);
    }

    private Long otherOwner() {
        return jdbc.queryForObject("select id from app_user where login = 'smith'", Long.class);
    }

    // ----------------------------------------------------------- устаревание и языки

    @Test
    void staleArtifactsAndLanguagesAreCounted() {
        Dashboard before = dashboard.build(ownerId);
        long ruBefore = language(before, "RU");
        String title = unique("Просрочен");
        Artifact a = artifact(title, Language.RU);
        jdbc.update("update artifact set review_due_at = ? where id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(3)), a.getId());

        Dashboard after = dashboard.build(ownerId);
        assertEquals(before.staleArtifacts() + 1, after.staleArtifacts());
        assertEquals(ruBefore + 1, language(after, "RU"));
        assertTrue(after.staleArtifactList().stream().anyMatch(i -> i.title().equals(title) && i.url().equals("/artifacts/" + a.getId())));
        assertEquals(0, dashboard.build(product.getId()).staleArtifactList().stream().filter(i -> i.title().equals(title)).count(),
                "чужой материал в цифры продакта не попадает");
    }

    private static long language(Dashboard d, String key) {
        return d.byLanguage().stream().filter(c -> c.key().equals(key)).mapToLong(Dashboard.Count::n).sum();
    }

    @Test
    void staleSkusAreCounted() {
        long before = dashboard.build(null).staleSkus();
        jdbc.update("update sku set next_review_date = ? where id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(2)),
                sku.getId());
        Dashboard after = dashboard.build(null);
        assertTrue(after.staleSkus() >= before, "просроченное ревью SKU попало в цифру");
        assertTrue(after.staleSkuList().stream().anyMatch(i -> i.title().equals("WEP-550K")) || after.staleSkus() > 10);
    }

    // ----------------------------------------------------------- вопросы и задачи

    @Test
    void questionsAndTasksAreCounted() {
        Dashboard before = dashboard.build(ownerId);
        var q = questions.ask(QuestionKind.QUESTION, TargetType.SKU, sku.getId(), "Сколько портов? " + unique("q"), false, sales);
        Dashboard open = dashboard.build(ownerId);
        assertEquals(before.openQuestions() + 1, open.openQuestions());

        questions.answer(q.getId(), "Два порта", true, product);
        Dashboard answered = dashboard.build(ownerId);
        assertEquals(before.openQuestions(), answered.openQuestions());
        assertEquals(before.answeredQuestions() + 1, answered.answeredQuestions());
        assertNotNull(answered.avgAnswerHours());

        // просроченная задача
        long overdueBefore = dashboard.build(null).overdueTasks();
        Long taskId = jdbc.queryForObject("select max(id) from task where status <> 'DONE'", Long.class);
        if (taskId != null) {
            jdbc.update("update task set due_date = ? where id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(5)), taskId);
            Dashboard d = dashboard.build(null);
            assertTrue(d.overdueTasks() >= overdueBefore);
            assertFalse(d.tasksByStatus().isEmpty());
        }
    }

    // ----------------------------------------------------------- спрос и просмотры

    @Test
    void demandAndViewsAreReported() {
        String found = unique("найденный");
        String lost = unique("потерянный");
        searchLog.record("PAGE", "sales", found, 3L, 5, null, null);
        searchLog.record("CLICK", "sales", found, null, 0, "SKU", sku.getId());
        searchLog.record("SUGGEST", "sales", lost, 0L, 2, null, null);
        searchLog.drain();

        for (int i = 0; i < 3; i++) {
            events.publishEvent(new ObjectViewed("SKU", sku.getId(), "sales"));
        }
        Artifact a = artifact(unique("Просмотренный"), Language.EN);
        events.publishEvent(new ObjectViewed("ARTIFACT", a.getId(), "sales"));
        viewLog.drain();

        Dashboard d = dashboard.build(null);
        assertTrue(d.topQueries().stream().anyMatch(p -> p.text().equals(found.toLowerCase()) && p.n() == 2), "запрос и клик по нему");
        assertTrue(d.zeroQueries().stream().anyMatch(p -> p.text().equals(lost.toLowerCase())), "запрос без результатов");
        assertFalse(d.topQueries().stream().anyMatch(p -> p.text().equals(lost.toLowerCase())));
        assertTrue(d.topSkus().stream().anyMatch(i -> i.title().equals("WEP-550K") && i.n() >= 3));
        assertTrue(d.topArtifacts().stream().anyMatch(i -> i.url().equals("/artifacts/" + a.getId()) && i.n() == 1));
        assertTrue(d.topSkus().size() <= 20 && d.topQueries().size() <= 20);
    }
}
