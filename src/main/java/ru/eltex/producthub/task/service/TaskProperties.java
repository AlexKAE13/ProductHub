package ru.eltex.producthub.task.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Сроки задач по умолчанию и сколько дней показывать закрытые на доске. */
@ConfigurationProperties(prefix = "producthub.tasks")
public record TaskProperties(
        @DefaultValue("7") int questionDueDays,
        @DefaultValue("14") int otherDueDays,
        @DefaultValue("14") int doneVisibleDays) {
}
