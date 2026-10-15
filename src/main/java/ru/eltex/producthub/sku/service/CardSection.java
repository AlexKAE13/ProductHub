package ru.eltex.producthub.sku.service;

/** Блоки карточки SKU (SPEC.md 5.3). Для каждого задано, можно ли показывать его во внешнем контуре. */
public enum CardSection {
    HEADER(true),
    DESCRIPTION(true),
    SPECS(true),
    LIFECYCLE(true),
    COMMERCIAL(false),
    PLANS(false),
    HISTORY(false);

    private final boolean external;

    CardSection(boolean external) {
        this.external = external;
    }

    public boolean isExternal() {
        return external;
    }
}
