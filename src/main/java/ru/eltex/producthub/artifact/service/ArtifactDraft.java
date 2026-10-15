package ru.eltex.producthub.artifact.service;

import java.util.Collection;
import java.util.List;

import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.ArtifactType;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.tag.domain.Tag;

/** Все, что нужно, чтобы создать артефакт (из формы, загрузки или импорта папки). */
public record ArtifactDraft(
        String title,
        String description,
        ArtifactType type,
        Language language,
        ArtifactStatus status,
        User owner,
        Contour contour,
        String externalUrl,
        ProductLine line,
        Collection<Sku> skus,
        Collection<Tag> tags,
        String sourceKey) {

    public ArtifactDraft {
        skus = skus == null ? List.of() : skus;
        tags = tags == null ? List.of() : tags;
    }
}
