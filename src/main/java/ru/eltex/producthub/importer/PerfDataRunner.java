package ru.eltex.producthub.importer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ru.eltex.producthub.search.service.SearchIndexer;

/**
 * Профиль perf (SPEC.md 8.1): докладывает данные до 300 SKU, 5000 артефактов (без файлов), 300 прошивок, 100 кейсов, 300 задач и по 20000 записей журналов просмотров и поиска
 * для замера скорости. Запуск: SPRING_PROFILES_ACTIVE=dev,perf, база и индекс лежат отдельно в ./data-perf.
 */
@Component
@Profile("perf")
@Order(Ordered.LOWEST_PRECEDENCE)
public class PerfDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfDataRunner.class);
    private static final int SKUS = 300;
    private static final int ARTIFACTS = 5000;
    private static final int FIRMWARE = 300;
    private static final int CASES = 100;
    private static final int TASKS = 300;
    private static final int LOG_ROWS = 20000;
    private static final String[] WORDS = {"настройка", "контроллер", "точка доступа", "мост", "антенна", "питание PoE", "Wi-Fi 6",
            "roaming", "mesh", "OLT", "GPON", "миграция", "сравнение с конкурентами", "презентация", "руководство", "datasheet",
            "release notes", "обновление", "резервирование", "отель", "склад", "школа", "стадион", "офис", "провайдер"};

    private final JdbcTemplate jdbc;
    private final SearchIndexer indexer;

    public PerfDataRunner(JdbcTemplate jdbc, SearchIndexer indexer) {
        this.jdbc = jdbc;
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        Random rnd = new Random(42);
        Long owner = jdbc.queryForObject("select min(id) from app_user", Long.class);
        List<Long> lines = jdbc.queryForList("select id from product_line", Long.class);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        int haveSkus = jdbc.queryForObject("select count(*) from sku", Integer.class);
        List<Object[]> skuRows = new ArrayList<>();
        for (int i = haveSkus; i < SKUS; i++) {
            String model = "PERF-" + (1000 + i);
            skuRows.add(new Object[] {model, model, "Тестовое изделие " + words(rnd, 3), lines.get(i % lines.size()), "Точка доступа внутренняя",
                    owner, "PRODUCTION", "INTERNAL", now, now});
        }
        jdbc.batchUpdate("insert into sku (model, model_normalized, name_ru, line_id, category, owner_id, lifecycle_status, contour,"
                + " created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", skuRows);
        List<Long> skuIds = jdbc.queryForList("select id from sku", Long.class);

        int haveArtifacts = jdbc.queryForObject("select count(*) from artifact", Integer.class);
        List<Long> typeIds = jdbc.queryForList("select id from artifact_type", Long.class);
        List<Object[]> artifacts = new ArrayList<>();
        for (int i = haveArtifacts; i < ARTIFACTS; i++) {
            artifacts.add(new Object[] {"Материал " + words(rnd, 3) + " " + i, words(rnd, 12), typeIds.get(i % typeIds.size()),
                    i % 3 == 0 ? "EN" : "RU", "ACTUAL", owner, i % 5 == 0 ? "EXTERNAL" : "INTERNAL", now, 90, now, "perf-" + i, "perf", now, now});
        }
        jdbc.batchUpdate("insert into artifact (title, description, type_id, language, status, owner_id, contour, last_reviewed_at,"
                + " review_period_days, review_due_at, source_key, created_by, created_at, updated_at)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", artifacts);
        List<Long> artifactIds = jdbc.queryForList("select id from artifact where source_key like 'perf-%' and id not in (select artifact_id from artifact_sku)", Long.class);
        List<Object[]> links = new ArrayList<>();
        for (Long id : artifactIds) {
            links.add(new Object[] {id, skuIds.get(rnd.nextInt(skuIds.size()))});
        }
        jdbc.batchUpdate("insert into artifact_sku (artifact_id, sku_id) values (?, ?)", links);

        int haveFirmware = jdbc.queryForObject("select count(*) from firmware", Integer.class);
        List<Object[]> fw = new ArrayList<>();
        for (int i = haveFirmware; i < FIRMWARE; i++) {
            fw.add(new Object[] {"7." + (i / 10) + "." + (i % 10), java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(i)), "RELEASE",
                    "Исправления: " + words(rnd, 10), i % 2 == 0, "perf", now, now, now});
        }
        jdbc.batchUpdate("insert into firmware (version, release_date, status, release_notes, is_public, published_by, published_at,"
                + " created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", fw);

        int haveCases = jdbc.queryForObject("select count(*) from case_study", Integer.class);
        List<Object[]> cs = new ArrayList<>();
        for (int i = haveCases; i < CASES; i++) {
            cs.add(new Object[] {"Кейс " + words(rnd, 3) + " " + i, "Казахстан", "Алматы", words(rnd, 15), owner, i % 2 == 0, "perf", now, now});
        }
        jdbc.batchUpdate("insert into case_study (title, country, city, summary, owner_id, publication_allowed, created_by, created_at,"
                + " updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", cs);

        int haveTasks = jdbc.queryForObject("select count(*) from task", Integer.class);
        String[] statuses = {"NEW", "IN_PROGRESS", "REVIEW", "DONE"};
        List<Object[]> tasks = new ArrayList<>();
        for (int i = haveTasks; i < TASKS; i++) {
            tasks.add(new Object[] {"Задача " + words(rnd, 4) + " " + i, "MANUAL", statuses[i % 4], owner, "perf", "NORMAL", now, now,
                    i % 4 == 3 ? now : null});
        }
        jdbc.batchUpdate("insert into task (title, task_type, status, assignee_id, created_by, priority, created_at, updated_at, closed_at)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)", tasks);

        // Журналы для дашборда: просмотры и запросы за последние 30 дней
        int haveViews = jdbc.queryForObject("select count(*) from view_log", Integer.class);
        List<Object[]> views = new ArrayList<>();
        for (int i = haveViews; i < LOG_ROWS; i++) {
            boolean sku = i % 2 == 0;
            Long id = sku ? skuIds.get(rnd.nextInt(skuIds.size())) : artifactIds.get(rnd.nextInt(artifactIds.size()));
            views.add(new Object[] {sku ? "SKU" : "ARTIFACT", id, "perf", Timestamp.valueOf(LocalDateTime.now().minusMinutes(rnd.nextInt(40000)))});
        }
        jdbc.batchUpdate("insert into view_log (entity_type, entity_id, user_login, at) values (?, ?, ?, ?)", views);
        int haveQueries = jdbc.queryForObject("select count(*) from search_log", Integer.class);
        List<Object[]> queries = new ArrayList<>();
        for (int i = haveQueries; i < LOG_ROWS; i++) {
            queries.add(new Object[] {i % 3 == 0 ? "PAGE" : "SUGGEST", "perf", words(rnd, 2), (long) rnd.nextInt(5), 5L,
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(rnd.nextInt(40000)))});
        }
        jdbc.batchUpdate("insert into search_log (kind, user_login, query_text, result_count, took_ms, at) values (?, ?, ?, ?, ?, ?)", queries);

        int docs = indexer.reindexAll();
        log.info("Профиль perf: SKU {}, артефактов {}, прошивок {}, кейсов {}, в индексе {} документов, готово за {} мс",
                skuIds.size(), jdbc.queryForObject("select count(*) from artifact", Integer.class),
                jdbc.queryForObject("select count(*) from firmware", Integer.class),
                jdbc.queryForObject("select count(*) from case_study", Integer.class), docs, System.currentTimeMillis() - start);
    }

    private static String words(Random rnd, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(WORDS[rnd.nextInt(WORDS.length)]).append(' ');
        }
        return sb.toString().trim();
    }
}
