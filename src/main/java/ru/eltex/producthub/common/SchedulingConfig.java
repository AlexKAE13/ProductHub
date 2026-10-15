package ru.eltex.producthub.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает @Scheduled (очистка планов импорта, позже ежедневные проверки устаревания). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
