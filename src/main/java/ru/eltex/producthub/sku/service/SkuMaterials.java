package ru.eltex.producthub.sku.service;

/** Порт: сведения о материалах SKU. Реализует модуль artifact, модуль sku о нем ничего не знает. */
public interface SkuMaterials {

    MaterialsSummary summary(Long skuId);
}
