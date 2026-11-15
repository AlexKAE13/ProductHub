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
        int page,
        int size) {

    public static final int PAGE_SIZE = 50;

    public ArtifactFilter(Long type, String language, String status, Long owner, String sku, Long line,
                          String family, Long tag, String contour, boolean reviewDue, String q,
                          String sort, String dir, int page) {
        this(type, language, status, owner, sku, line, family, tag, contour, reviewDue, q, sort, dir, page, PAGE_SIZE);
    }

    public ArtifactFilter {
        if (size != 50 && size != 100 && size != 0) size = PAGE_SIZE;
    }
}
