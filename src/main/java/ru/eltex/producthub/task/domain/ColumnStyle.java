package ru.eltex.producthub.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Название и цвет колонки на своей доске. Цвет - ключ из палитры {@link BoardColor}. */
@Embeddable
public class ColumnStyle {

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 16)
    private String color;

    protected ColumnStyle() {
    }

    public ColumnStyle(String title, String color) {
        this.title = title;
        this.color = color;
    }

    public String getTitle() {
        return title;
    }

    public String getColor() {
        return color;
    }
}
