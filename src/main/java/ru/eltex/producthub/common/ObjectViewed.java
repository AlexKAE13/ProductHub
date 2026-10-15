package ru.eltex.producthub.common;

/** Событие: пользователь открыл карточку SKU или страницу материала. Модуль dashboard пишет его в ViewLog. */
public record ObjectViewed(String type, Long id, String login) {
}
