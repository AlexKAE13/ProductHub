package ru.eltex.producthub.settings;

/** Тип фона современного оформления. */
public enum Background {
    MESH, FLAT, DOTS, PHOTO;

    public String css() {
        return name().toLowerCase();
    }
}
