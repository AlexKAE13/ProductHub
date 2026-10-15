package ru.eltex.producthub.artifact.service;

/** Параметры списка артефактов (хранятся в URL). */
public record ArtifactFilter(
        Long type,
        String language,
        String status,
        Long owner,
        String sku,
        Long line,
        String family,
        Long tag,
        String contour,
        boolean reviewDue,
        String q,
        String sort,
        String dir,
        int page) {

    public static final int PAGE_SIZE = 50;
}
