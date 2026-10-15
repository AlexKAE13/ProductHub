package ru.eltex.producthub.sku.event;

/** Событие: у SKU прошла дата следующего ревью (ежедневная проверка). */
public record SkuReviewDue(Long skuId) {
}
