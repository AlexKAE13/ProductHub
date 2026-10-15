package ru.eltex.producthub.task.service;

import java.util.List;
import java.util.Map;

import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskStatus;

/** Доска: задачи по колонкам и признаки прав. */
public record Board(Map<TaskStatus, List<Task>> columns, boolean canMoveAll, boolean canSeeAll) {
}
