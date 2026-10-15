package ru.eltex.producthub.casestudy.service;

import java.util.List;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.casestudy.domain.CaseStudy;
import ru.eltex.producthub.common.AuditLog;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.tag.domain.Tag;

/**
 * Страница кейса. Имя заказчика уже скрыто сервисом, если пользователю его видеть нельзя
 * ({@code customerName == null}, {@code customerHidden == true}).
 */
public record CaseDetail(
        CaseStudy caseStudy,
        String customerName,
        boolean customerHidden,
        List<Sku> skus,
        List<Tag> tags,
        List<Attachment> attachments,
        List<AuditLog> history,
        boolean canEdit) {

    public record Attachment(Artifact artifact, ArtifactFile main, ArtifactFile preview) {
    }
}
