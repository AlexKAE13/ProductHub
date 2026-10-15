package ru.eltex.producthub.sku.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecDefinition;
import ru.eltex.producthub.sku.domain.SpecValue;

/**
 * Процент заполненности карточки (SPEC.md 5.4). На этапе 1 считаются только части, данные которых уже есть:
 * идентификация, ЖЦ, характеристики, коммерческий блок. Даташиты и фото (этап 2) входят, когда модуль материалов
 * передал сведения. Прошивки (вес firmware) подключаются на этапе 3, до тех пор итог считается по весам имеющихся частей.
 */
@Component
public class CompletenessCalculator {

    private final CompletenessProperties weights;

    public CompletenessCalculator(CompletenessProperties weights) {
        this.weights = weights;
    }

    public int compute(Sku sku, Collection<SpecValue> values, List<SpecDefinition> template, LocalDateTime now) {
        return compute(sku, values, template, now, null);
    }

    /** @param materials сведения о материалах; null - модуль материалов не учитывается (части datasheets и photos не входят) */
    public int compute(Sku sku, Collection<SpecValue> values, List<SpecDefinition> template, LocalDateTime now,
                       MaterialsSummary materials) {
        double sum = 0;
        double total = 0;

        total += weights.getIdentification();
        sum += weights.getIdentification() * identification(sku);

        total += weights.getLifecycle();
        sum += weights.getLifecycle() * lifecycle(sku);

        if (!template.isEmpty()) {
            Set<Long> filled = values.stream().map(v -> v.getDefinition().getId()).collect(Collectors.toSet());
            long hit = template.stream().filter(d -> filled.contains(d.getId())).count();
            total += weights.getSpecs();
            sum += weights.getSpecs() * ((double) hit / template.size());
        }

        total += weights.getCommercial();
        sum += weights.getCommercial() * commercial(sku, now);

        if (materials != null) {
            total += weights.getDatasheets();
            sum += weights.getDatasheets() * ((materials.datasheetRu() ? 1 : 0) + (materials.datasheetEn() ? 1 : 0)) / 2.0;
            total += weights.getPhotos();
            sum += weights.getPhotos() * (materials.photo() ? 1 : 0);
        }

        return total == 0 ? 0 : (int) Math.round(100 * sum / total);
    }

    private double identification(Sku sku) {
        int filled = 0;
        filled += has(sku.getNameRu()) ? 1 : 0;
        filled += has(sku.getNameEn()) ? 1 : 0;
        filled += has(sku.getSiteSectionRu()) ? 1 : 0;
        filled += has(sku.getChip()) ? 1 : 0;
        filled += sku.getLine() != null ? 1 : 0;
        filled += has(sku.getCategory()) ? 1 : 0;
        filled += sku.getOwner() != null ? 1 : 0;
        return filled / 7.0;
    }

    private double lifecycle(Sku sku) {
        int required = 4;
        int filled = 1; // статус обязателен и всегда есть
        filled += has(sku.getGaDate()) ? 1 : 0;
        filled += sku.getLastReviewDate() != null ? 1 : 0;
        filled += sku.getNextReviewDate() != null ? 1 : 0;
        LifecycleStatus status = sku.getLifecycleStatus();
        if (status == LifecycleStatus.EOL_ANNOUNCED || status == LifecycleStatus.EOL) {
            required += 4;
            filled += has(sku.getEolAnnounceDate()) ? 1 : 0;
            filled += has(sku.getLastOrderDate()) ? 1 : 0;
            filled += has(sku.getLastShipDate()) ? 1 : 0;
            filled += has(sku.getEndOfSupportDate()) ? 1 : 0;
        }
        return (double) filled / required;
    }

    private double commercial(Sku sku, LocalDateTime now) {
        if (sku.getPrice() == null || sku.getAvailability() == null || sku.getCommercialUpdatedAt() == null) {
            return 0;
        }
        long age = ChronoUnit.DAYS.between(sku.getCommercialUpdatedAt(), now);
        return age <= weights.getCommercialMaxAgeDays() ? 1 : 0;
    }

    public boolean isCommercialStale(Sku sku, LocalDate today) {
        return sku.getCommercialUpdatedAt() == null
                || ChronoUnit.DAYS.between(sku.getCommercialUpdatedAt().toLocalDate(), today)
                > weights.getCommercialMaxAgeDays();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
