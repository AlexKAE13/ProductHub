package ru.eltex.producthub.task.service;

import ru.eltex.producthub.security.User;
import ru.eltex.producthub.task.domain.RelatedType;
import ru.eltex.producthub.task.domain.TaskType;

/** Описание задачи, которую создает триггер. */
public record TaskSpec(TaskType type, String title, String description, User assignee, RelatedType relatedType,
                       Long relatedId, String relatedLabel, String relatedUrl, Long skuId, String sourceEvent) {
}
