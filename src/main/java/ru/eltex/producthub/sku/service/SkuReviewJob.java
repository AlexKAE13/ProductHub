package ru.eltex.producthub.sku.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.sku.event.SkuReviewDue;
import ru.eltex.producthub.sku.repo.SkuRepository;

/** Ежедневная проверка: у каких SKU прошла дата следующего ревью (SPEC.md 5.9). */
@Component
public class SkuReviewJob {

    private static final Logger log = LoggerFactory.getLogger(SkuReviewJob.class);

    private final SkuRepository skus;
    private final ApplicationEventPublisher events;

    public SkuReviewJob(SkuRepository skus, ApplicationEventPublisher events) {
        this.skus = skus;
        this.events = events;
    }

    @Scheduled(cron = "${producthub.schedule.daily-checks:0 0 7 * * *}")
    @Transactional
    public int run() {
        List<Long> due = skus.findReviewDueIds(LocalDate.now());
        due.forEach(id -> events.publishEvent(new SkuReviewDue(id)));
        log.info("Проверка ревью SKU: просрочено {}", due.size());
        return due.size();
    }
}
