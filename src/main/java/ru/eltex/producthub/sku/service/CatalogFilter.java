package ru.eltex.producthub.sku.service;

/** Параметры каталога. Хранятся в URL, поэтому все поля простые. */
public record CatalogFilter(
        String family,
        String line,
        String category,
        String status,
        Long owner,
        Long tag,
        boolean reviewDue,
        boolean includeEol,
        String q,
        String sort,
        String dir,
        int page) {

    public static final int PAGE_SIZE = 50;

    public CatalogFilter withPage(int newPage) {
        return new CatalogFilter(family, line, category, status, owner, tag, reviewDue, includeEol, q, sort, dir, newPage);
    }
}
