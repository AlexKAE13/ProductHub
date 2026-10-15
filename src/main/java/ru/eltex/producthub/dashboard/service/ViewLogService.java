package ru.eltex.producthub.dashboard.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.eltex.producthub.common.ObjectViewed;

/** Журнал просмотров (SPEC.md 4.10). Запись идет в фоне, чтобы не тормозить открытие страницы. */
@Service
public class ViewLogService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ViewLogService.class);

    private final JdbcTemplate jdbc;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "view-log");
        t.setDaemon(true);
        return t;
    });

    public ViewLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    public void viewed(ObjectViewed event) {
        Timestamp at = Timestamp.valueOf(LocalDateTime.now());
        writer.execute(() -> {
            try {
                jdbc.update("insert into view_log (entity_type, entity_id, user_login, at) values (?, ?, ?, ?)", event.type(),
                        event.id(), event.login(), at);
            } catch (RuntimeException e) {
                log.warn("Не удалось записать просмотр: {}", e.getMessage());
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

    @Override
    public void destroy() {
        writer.shutdown();
    }
}
