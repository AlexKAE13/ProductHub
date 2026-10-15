package ru.eltex.producthub.common;

/** Событие: сохранена сущность, попадающая в поиск. Модуль search обновляет индекс после коммита. */
public record IndexedEntityChanged(String type, Long id) {
}
