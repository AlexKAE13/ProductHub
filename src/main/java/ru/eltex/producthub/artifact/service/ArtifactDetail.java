package ru.eltex.producthub.artifact.service;

import java.util.List;
import java.util.Map;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.domain.ArtifactVersion;
import ru.eltex.producthub.common.AuditLog;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.tag.domain.Tag;

/** Страница артефакта: метаданные, версии с файлами, связи, история. */
public record ArtifactDetail(
        Artifact artifact,
        List<ArtifactVersion> versions,
        Map<Long, List<ArtifactFile>> filesByVersion,
        List<Sku> skus,
        List<Tag> tags,
        List<AuditLog> history,
        boolean canEdit,
        boolean canManage,
        boolean reviewDue) {

    public List<ArtifactFile> currentFiles() {
        if (artifact.getCurrentVersion() == null) {
            return List.of();
        }
        return filesByVersion.getOrDefault(artifact.getCurrentVersion().getId(), List.of());
    }

    /** Файл для встроенного просмотра: pdf-предпросмотр, иначе основной pdf или картинка. */
    public ArtifactFile previewFile() {
        List<ArtifactFile> files = currentFiles();
        ArtifactFile preview = files.stream().filter(f -> f.getRole() == ru.eltex.producthub.artifact.domain.FileRole.PREVIEW)
                .findFirst().orElse(null);
        if (preview != null) {
            return preview;
        }
        return files.stream().filter(f -> f.getRole() == ru.eltex.producthub.artifact.domain.FileRole.MAIN
                && ("application/pdf".equals(f.getMimeType()) || "image/png".equals(f.getMimeType())
                || "image/jpeg".equals(f.getMimeType()))).findFirst().orElse(null);
    }
}
