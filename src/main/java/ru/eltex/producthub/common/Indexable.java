package ru.eltex.producthub.common;

/** Сущность, изменения которой нужно отразить в поисковом индексе (SPEC.md 5.10). */
public interface Indexable {

    /** Тип документа в индексе: SKU, ARTIFACT, FIRMWARE, CASE, FAQ. */
    String indexType();

    /** Идентификатор документа (для характеристик это идентификатор SKU). */
    Long indexId();
}
