package ru.eltex.producthub.task.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ru.eltex.producthub.task.domain.Task;
import ru.eltex.producthub.task.domain.TaskStatus;

/**
 * Доска: задачи по колонкам и признаки прав. Для системной доски "Входящие запросы" boardId пуст, названия и цвета
 * колонок берутся из стандартных (titles и colors пустые). Для своей доски они приходят из настроек доски.
 */
public record Board(Map<TaskStatus, List<Task>> columns, boolean canMoveAll, boolean canSeeAll,
                    Long boardId, String name, Map<TaskStatus, String> titles, Map<TaskStatus, String> colors,
                    boolean archive) {

    /** Доска без своих настроек (Входящие запросы). */
    public Board(Map<TaskStatus, List<Task>> columns, boolean canMoveAll, boolean canSeeAll) {
        this(columns, canMoveAll, canSeeAll, null, null, Map.of(), Map.of(), false);
    }

    /** Все задачи одним списком (для архива): сначала свежие по дате архивации. */
    public List<Task> flat() {
        return columns.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparing(Task::getArchivedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Task::getId, Comparator.reverseOrder()))
                .toList();
    }
}
