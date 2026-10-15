package ru.eltex.producthub.feedback.domain;

/** Судьба комментария: новый, принят в работу, отклонен, сделан. */
public enum CommentStatus {
    NEW,
    ACCEPTED,
    REJECTED,
    DONE
}
