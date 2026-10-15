package ru.eltex.producthub.task.domain;

/** Типы задач (SPEC.md 4.9). */
public enum TaskType {
    ANSWER_QUESTION, REVIEW_EDIT, CHECK_DATASHEET, CHECK_BATTLECARD, UPDATE_SITE, REVIEW_STALE_ARTIFACT, REVIEW_SKU,
    TRANSLATE, MANUAL
}
