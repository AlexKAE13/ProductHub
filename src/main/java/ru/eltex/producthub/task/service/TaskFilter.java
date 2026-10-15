package ru.eltex.producthub.task.service;

/** Фильтры доски. assignee: "me" (по умолчанию), "all" или id пользователя. */
public record TaskFilter(String assignee, String type, String sku, Long line) {

    public static TaskFilter defaults() {
        return new TaskFilter("me", null, null, null);
    }
}
