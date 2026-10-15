package ru.eltex.producthub.artifact.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.repo.ArtifactFileRepository;
import ru.eltex.producthub.artifact.repo.ArtifactQueryRepository;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.tag.domain.Tag;

/** Список артефактов: страница результатов плюс связанные данные тремя дополнительными запросами (без N+1). */
@Service
public class ArtifactListService {

    private final ArtifactQueryRepository queries;
    private final ArtifactRepository artifacts;
    private final ArtifactFileRepository files;
    private final ArtifactPermissions permissions;

    public ArtifactListService(ArtifactQueryRepository queries, ArtifactRepository artifacts,
                               ArtifactFileRepository files, ArtifactPermissions permissions) {
        this.queries = queries;
        this.artifacts = artifacts;
        this.files = files;
        this.permissions = permissions;
    }

    public record View(ArtifactPage page, Map<Long, ArtifactFile> mainFiles, Map<Long, List<Sku>> skus,
                       Map<Long, List<Tag>> tags) {
    }

    @Transactional(readOnly = true)
    public View search(ArtifactFilter filter, HubUserDetails user) {
        ArtifactPage page = queries.search(filter, !permissions.isInternal(user));
        List<Long> ids = page.items().stream().map(Artifact::getId).toList();
        Map<Long, ArtifactFile> main = new HashMap<>();
        Map<Long, List<Sku>> skus = new HashMap<>();
        Map<Long, List<Tag>> tags = new HashMap<>();
        if (!ids.isEmpty()) {
            Map<Long, Long> versionToArtifact = page.items().stream().filter(a -> a.getCurrentVersion() != null)
                    .collect(Collectors.toMap(a -> a.getCurrentVersion().getId(), Artifact::getId));
            if (!versionToArtifact.isEmpty()) {
                for (ArtifactFile f : files.findByVersionIds(versionToArtifact.keySet())) {
                    if (f.getRole() == FileRole.MAIN) {
                        main.put(versionToArtifact.get(f.getVersion().getId()), f);
                    }
                }
            }
            for (Object[] r : artifacts.findSkusByArtifactIds(ids)) {
                skus.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((Sku) r[1]);
            }
            for (Object[] r : artifacts.findTagsByArtifactIds(ids)) {
                tags.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((Tag) r[1]);
            }
        }
        return new View(page, main, skus, tags);
    }
}
