package ru.eltex.producthub.feedback.service;

/** Что присылает браузер при создании комментария. Контекст элемента нужен, чтобы по выгрузке находить место в коде. */
public record CommentInput(String pagePath, String pageQuery, String pageTitle, String route, String view, String selector,
                           String tag, String text, String html, String section, String context, String viewport, String kind,
                           String body, Long parentId) {
}
