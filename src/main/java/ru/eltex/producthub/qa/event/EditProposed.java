package ru.eltex.producthub.qa.event;

/** Событие: предложена правка. Модуль задач создает задачу проверки владельцу объекта. */
public record EditProposed(Long questionId) {
}
