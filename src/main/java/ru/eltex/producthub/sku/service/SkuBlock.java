package ru.eltex.producthub.sku.service;

/** Блоки карточки, которые правятся на месте. */
public enum SkuBlock {
    MAIN, TEXT, SPECS, LIFECYCLE, COMMERCIAL, PLANS;

    public static SkuBlock parse(String code) {
        return valueOf(code.toUpperCase(java.util.Locale.ROOT));
    }
}
