package ru.eltex.producthub.artifact.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.event.ArtifactStale;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;

/** Ежедневная проверка устаревания материалов (SPEC.md 5.9, 07:00). Статус артефакта не меняется. */
@Component
public class ArtifactStalenessJob {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStalenessJob.class);

    private final ArtifactRepository artifacts;
    private final ApplicationEventPublisher events;

    public ArtifactStalenessJob(ArtifactRepository artifacts, ApplicationEventPublisher events) {
        this.artifacts = artifacts;
        this.events = events;
    }

    @Scheduled(cron = "${producthub.schedule.daily-checks:0 0 7 * * *}")
    @Transactional
    public int run() {
        List<Long> stale = artifacts.findStaleIds(LocalDateTime.now());
        stale.forEach(id -> events.publishEvent(new ArtifactStale(id)));
        log.info("Проверка устаревания артефактов: просрочено {}", stale.size());
        return stale.size();
    }
}
