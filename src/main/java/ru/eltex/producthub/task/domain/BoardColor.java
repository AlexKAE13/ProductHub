package ru.eltex.producthub.task.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Палитра цветов колонок своих досок. В базе хранится ключ, сам цвет задан в CSS (класс tone-ключ). */
public final class BoardColor {

    public static final Set<String> KEYS = Arrays.stream(new String[] {"blue", "cyan", "green", "amber", "red", "violet", "grey"})
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

    private BoardColor() {
    }

    public static String defaultFor(TaskStatus status) {
        return switch (status) {
            case NEW -> "blue";
            case IN_PROGRESS -> "cyan";
            case REVIEW -> "amber";
            case DONE -> "green";
        };
    }
}
