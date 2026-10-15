package ru.eltex.producthub.sku.service;

import java.util.List;
import java.util.Set;

import ru.eltex.producthub.common.AuditLog;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecDefinition;

/** Все, что нужно шаблону карточки SKU. Собирается {@link SkuService#loadCard} за несколько запросов. */
public record SkuCard(
        Sku sku,
        List<SpecGroup> specGroups,
        List<Sku> predecessors,
        List<AuditLog> history,
        Set<CardSection> visible,
        boolean canEdit,
        boolean canEditSpecs,
        boolean commercialStale,
        int emptySpecs,
        List<ru.eltex.producthub.tag.domain.Tag> tags) {

    public boolean can(CardSection section) {
        return visible.contains(section);
    }

    /** Для шаблонов: {@code card.can('PLANS')}. */
    public boolean can(String section) {
        return visible.contains(CardSection.valueOf(section));
    }

    public record SpecGroup(String name, List<SpecRow> rows) {
    }

    public record SpecRow(SpecDefinition definition, String value, boolean editable) {
        public boolean isEmpty() {
            return value == null || value.isBlank();
        }
    }
}
