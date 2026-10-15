package ru.eltex.producthub.qa.event;

/** Событие: задан новый вопрос (SPEC.md 3.2). Модуль задач создает задачу ответа владельцу объекта. */
public record QuestionCreated(Long questionId) {
}
