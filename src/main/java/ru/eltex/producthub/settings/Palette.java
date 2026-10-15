package ru.eltex.producthub.settings;

/** Цветовая схема современного оформления. Цвета статусов от схемы не зависят. */
public enum Palette {
    ELTEX, AZURE, LILAC, PINE, AMBER, GRAPHITE;

    public String css() {
        return name().toLowerCase();
    }
}
