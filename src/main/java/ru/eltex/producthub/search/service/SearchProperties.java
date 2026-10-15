package ru.eltex.producthub.search.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Настройки поиска. Пустая папка индекса означает индекс в памяти (для тестов). */
@ConfigurationProperties(prefix = "producthub.search")
public record SearchProperties(@DefaultValue("") String dir,
                               @DefaultValue("5") int groupLimit,
                               @DefaultValue("20") int pageSize,
                               @DefaultValue("2") int minLength,
                               @DefaultValue("200") int bulkThreshold) {
}
