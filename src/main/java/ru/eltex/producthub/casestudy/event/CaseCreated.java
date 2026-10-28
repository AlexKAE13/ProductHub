package ru.eltex.producthub.casestudy.event;

/** Добавлен новый кейс. Слушает модуль задач: владельцам SKU кейса приходит задача проверить его. */
public record CaseCreated(Long caseId) {
}
