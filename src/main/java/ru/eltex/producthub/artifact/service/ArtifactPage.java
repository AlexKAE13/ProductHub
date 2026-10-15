package ru.eltex.producthub.artifact.service;

import java.util.List;

import ru.eltex.producthub.artifact.domain.Artifact;

public record ArtifactPage(List<Artifact> items, long total, int page, int pageSize) {

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
