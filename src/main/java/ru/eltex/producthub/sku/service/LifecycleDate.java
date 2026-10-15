package ru.eltex.producthub.sku.service;

import java.util.regex.Pattern;

/** Даты ЖЦ хранятся текстом: квартал ("2Q26") или дата ("2026-07-16"), как в реестре. */
public final class LifecycleDate {

    private static final Pattern QUARTER = Pattern.compile("^[1-4][Qq]\\d{2}$");
    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private LifecycleDate() {
    }

    public static boolean isValid(String value) {
        return value != null && (QUARTER.matcher(value).matches() || ISO.matcher(value).matches());
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty() || v.equals("-")) {
            return null;
        }
        return QUARTER.matcher(v).matches() ? v.toUpperCase(java.util.Locale.ROOT) : v;
    }
}
