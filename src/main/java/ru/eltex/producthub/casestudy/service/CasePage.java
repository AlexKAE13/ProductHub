package ru.eltex.producthub.casestudy.service;

import java.util.List;

import ru.eltex.producthub.casestudy.domain.CaseStudy;

public record CasePage(List<CaseStudy> items, long total, int page, int pageSize) {

    public int totalPages() {
        return (int) Math.max(1, (total + pageSize - 1) / pageSize);
    }

    public boolean hasPrev() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }
}
