package ru.eltex.producthub.search.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Журнал поиска (SPEC.md 5.10): запросы и клики по результатам. Пишется в фоне, чтобы не тормозить ответ. */
@Service
public class SearchLogService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);

    private final JdbcTemplate jdbc;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "search-log");
        t.setDaemon(true);
        return t;
    });

    public SearchLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** kind: SUGGEST (выпадающий список), PAGE (страница результатов), CLICK. */
    public void record(String kind, String login, String query, Long resultCount, long millis, String targetType, Long targetId) {
        LocalDateTime at = LocalDateTime.now();
        writer.execute(() -> {
            try {
                jdbc.update("insert into search_log (kind, user_login, query_text, result_count, took_ms, target_type, target_id, at)"
                                + " values (?, ?, ?, ?, ?, ?, ?, ?)", kind, login, truncate(query), resultCount, millis,
                        targetType, targetId, Timestamp.valueOf(at));
            } catch (RuntimeException e) {
                log.warn("Не удалось записать журнал поиска: {}", e.getMessage());
            }
        });
    }

    /** Ждет, пока фоновая запись догонит очередь (нужно тестам). */
    public void drain() {
        try {
            writer.submit(() -> {
            }).get();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s;
    }

    @Override
    public void destroy() {
        writer.shutdown();
    }
}
