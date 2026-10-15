package ru.eltex.producthub.feedback.service;

import java.util.List;

/** Комментарий для показа на странице. */
public record CommentView(long id, Long parentId, String authorName, String authorLogin, String kind, String status, String body,
                          String selector, String text, String section, String createdAt, String resolutionNote,
                          boolean onElement, String pagePath, String pageQuery, String pageTitle, List<CommentView> replies) {
}
