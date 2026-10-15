package ru.eltex.producthub.sku.service;

import java.util.Locale;

/** Нормализация артикула для поиска и сопоставления: "WEP 550k" и "wep-550k" дают "WEP550K" (SPEC.md 4.2, 5.10). */
public final class ModelNormalizer {

    private ModelNormalizer() {
    }

    public static String normalize(String model) {
        if (model == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(model.length());
        for (char c : model.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == '-' || c == '_' || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
