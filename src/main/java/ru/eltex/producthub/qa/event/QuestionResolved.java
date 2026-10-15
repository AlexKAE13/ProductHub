package ru.eltex.producthub.qa.event;

/** Событие: на вопрос ответили или правку приняли либо отклонили. Связанная задача закрывается. */
public record QuestionResolved(Long questionId) {
}
