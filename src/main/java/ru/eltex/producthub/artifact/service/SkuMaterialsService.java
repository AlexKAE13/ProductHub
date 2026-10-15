package ru.eltex.producthub.artifact.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.ArtifactType;
import ru.eltex.producthub.artifact.domain.CardBlock;
import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.repo.ArtifactFileRepository;
import ru.eltex.producthub.artifact.repo.ArtifactQueryRepository;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.domain.TagCategory;

/** Материалы в карточке SKU: блоки 5 (документация), 6 (сравнения и battle cards), 9 (фото и видео). */
@Service
public class SkuMaterialsService {

    private final ArtifactQueryRepository queries;
    private final ArtifactFileRepository files;
    private final ArtifactRepository artifacts;
    private final ArtifactPermissions permissions;

    public SkuMaterialsService(ArtifactQueryRepository queries, ArtifactFileRepository files,
                               ArtifactRepository artifacts, ArtifactPermissions permissions) {
        this.queries = queries;
        this.files = files;
        this.artifacts = artifacts;
        this.permissions = permissions;
    }

    public record Item(Artifact artifact, ArtifactFile main, ArtifactFile preview, List<Tag> competitors) {

        public boolean isOutdated() {
            return artifact.getStatus() == ArtifactStatus.OUTDATED || artifact.getStatus() == ArtifactStatus.EOL;
        }
    }

    public record TypeGroup(ArtifactType type, List<Item> actual, List<Item> outdated) {
    }

    public record View(List<TypeGroup> docs, TypeGroup compare, List<Item> media, String lang) {

        public boolean isEmpty() {
            return docs.isEmpty() && compare.actual().isEmpty() && compare.outdated().isEmpty() && media.isEmpty();
        }
    }

    /**
     * @param lang ALL, RU или EN. Материал на RU_EN или без языка показывается при любом фильтре.
     */
    @Transactional(readOnly = true)
    public View forSku(Long skuId, Long lineId, String lang, HubUserDetails user) {
        String filter = lang == null || lang.isBlank() ? "ALL" : lang.toUpperCase();
        List<Artifact> all = queries.findForSkuCard(skuId, lineId, !permissions.isInternal(user));
        if (all.isEmpty()) {
            return new View(List.of(), new TypeGroup(null, List.of(), List.of()), List.of(), filter);
        }
        List<Long> versionIds = all.stream().filter(a -> a.getCurrentVersion() != null)
                .map(a -> a.getCurrentVersion().getId()).toList();
        Map<Long, List<ArtifactFile>> byVersion = versionIds.isEmpty() ? Map.of()
                : files.findByVersionIds(versionIds).stream()
                .collect(Collectors.groupingBy(f -> f.getVersion().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<Tag>> competitors = new HashMap<>();
        for (Object[] row : artifacts.findTagsByArtifactIds(all.stream().map(Artifact::getId).toList())) {
            Tag tag = (Tag) row[1];
            if (tag.getCategory() == TagCategory.COMPETITOR) {
                competitors.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(tag);
            }
        }

        Map<ArtifactType, TypeGroup> docs = new LinkedHashMap<>();
        List<Item> compareActual = new ArrayList<>();
        List<Item> compareOutdated = new ArrayList<>();
        List<Item> media = new ArrayList<>();
        for (Artifact a : all) {
            if (!matches(a.getLanguage(), filter)) {
                continue;
            }
            List<ArtifactFile> fs = a.getCurrentVersion() == null ? List.of()
                    : byVersion.getOrDefault(a.getCurrentVersion().getId(), List.of());
            ArtifactFile main = fs.stream().filter(f -> f.getRole() == FileRole.MAIN).findFirst().orElse(null);
            ArtifactFile preview = fs.stream().filter(f -> f.getRole() == FileRole.PREVIEW).findFirst().orElse(null);
            Item item = new Item(a, main, preview, competitors.getOrDefault(a.getId(), List.of()));
            CardBlock block = a.getType().getCardBlock();
            switch (block) {
                case COMPARE -> (item.isOutdated() ? compareOutdated : compareActual).add(item);
                case MEDIA -> media.add(item);
                default -> {
                    TypeGroup g = docs.computeIfAbsent(a.getType(),
                            t -> new TypeGroup(t, new ArrayList<>(), new ArrayList<>()));
                    (item.isOutdated() ? g.outdated() : g.actual()).add(item);
                }
            }
        }
        return new View(new ArrayList<>(docs.values()), new TypeGroup(null, compareActual, compareOutdated), media,
                filter);
    }

    private static boolean matches(Language language, String filter) {
        if (filter.equals("ALL")) {
            return true;
        }
        return language == Language.RU_EN || language == Language.NONE || language.name().equals(filter);
    }

    public static Set<String> languages() {
        return Set.of("ALL", "RU", "EN");
    }
}
