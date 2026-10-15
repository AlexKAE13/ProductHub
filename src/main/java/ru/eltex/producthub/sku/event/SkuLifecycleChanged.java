package ru.eltex.producthub.sku.event;

import ru.eltex.producthub.sku.domain.LifecycleStatus;

/** Событие: у SKU сменился статус жизненного цикла (SPEC.md 3.2). */
public record SkuLifecycleChanged(Long skuId, LifecycleStatus oldStatus, LifecycleStatus newStatus, String userLogin) {
}
