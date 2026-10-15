package ru.eltex.producthub.sku.service;

/** Что есть из материалов у SKU (нужно для заполненности). Заполняет модуль artifact. */
public record MaterialsSummary(boolean datasheetRu, boolean datasheetEn, boolean photo) {

    public static final MaterialsSummary NONE = new MaterialsSummary(false, false, false);
}
