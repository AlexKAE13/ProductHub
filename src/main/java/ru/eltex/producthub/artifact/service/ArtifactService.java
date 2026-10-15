package ru.eltex.producthub.artifact.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.ArtifactType;
import ru.eltex.producthub.artifact.domain.ArtifactVersion;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.event.ArtifactUpdated;
import ru.eltex.producthub.artifact.repo.ArtifactFileRepository;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.artifact.repo.ArtifactVersionRepository;
import ru.eltex.producthub.artifact.storage.FileProperties;
import ru.eltex.producthub.artifact.storage.FileStorage;
import ru.eltex.producthub.artifact.storage.FileTypes;
import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.repo.ProductLineRepository;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.repo.TagRepository;

/** Бизнес-логика артефактов: создание, версии, правка, подтверждение актуальности, удаление, массовые действия. */
@Service
public class ArtifactService {

    public static final String ENTITY = "ARTIFACT";
    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);
    private static final int HISTORY_LIMIT = 50;

    private final ArtifactRepository artifacts;
    private final ArtifactVersionRepository versions;
    private final ArtifactFileRepository files;
    private final FileStorage storage;
    private final FileProperties fileProps;
    private final ArtifactTypeService types;
    private final ArtifactPermissions permissions;
    private final SkuRepository skus;
    private final ProductLineRepository lines;
    private final UserRepository users;
    private final TagRepository tags;
    private final AuditService audit;
    private final SkuService skuService;
    private final ApplicationEventPublisher events;

    public ArtifactService(ArtifactRepository artifacts, ArtifactVersionRepository versions,
                           ArtifactFileRepository files, FileStorage storage, FileProperties fileProps,
                           ArtifactTypeService types, ArtifactPermissions permissions, SkuRepository skus,
                           ProductLineRepository lines, UserRepository users, TagRepository tags, AuditService audit,
                           SkuService skuService, ApplicationEventPublisher events) {
        this.artifacts = artifacts;
        this.versions = versions;
        this.files = files;
        this.storage = storage;
        this.fileProps = fileProps;
        this.types = types;
        this.permissions = permissions;
        this.skus = skus;
        this.lines = lines;
        this.users = users;
        this.tags = tags;
        this.audit = audit;
        this.skuService = skuService;
        this.events = events;
    }

    // ------------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public ArtifactDetail detail(Long id, HubUserDetails user) {
        Artifact a = load(id);
        if (!permissions.canView(user, a)) {
            throw new AccessDeniedException("Артефакт недоступен во внешнем контуре");
        }
        List<ArtifactVersion> vs = versions.findByArtifactIdOrderByVersionNoDesc(id);
        Map<Long, List<ArtifactFile>> byVersion = new LinkedHashMap<>();
        if (!vs.isEmpty()) {
            files.findByVersionIds(vs.stream().map(ArtifactVersion::getId).toList())
                    .forEach(f -> byVersion.computeIfAbsent(f.getVersion().getId(), k -> new ArrayList<>()).add(f));
        }
        List<Sku> linked = new ArrayList<>();
        artifacts.findSkusByArtifactIds(List.of(id)).forEach(r -> linked.add((Sku) r[1]));
        List<Tag> tagList = artifacts.findTags(id);
        var history = permissions.isInternal(user) ? audit.history(ENTITY, id, HISTORY_LIMIT)
                : List.<ru.eltex.producthub.common.AuditLog>of();
        return new ArtifactDetail(a, vs, byVersion, linked, tagList, history, permissions.canEdit(user),
                permissions.canManage(user), a.getReviewDueAt().isBefore(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public ArtifactForm formFor(Long id, HubUserDetails user) {
        Artifact a = load(id);
        if (!permissions.canView(user, a)) {
            throw new AccessDeniedException("Артефакт недоступен");
        }
        ArtifactForm f = new ArtifactForm();
        f.setTitle(a.getTitle());
        f.setDescription(a.getDescription());
        f.setTypeId(a.getType().getId());
        f.setLanguage(a.getLanguage().name());
        f.setStatus(a.getStatus().name());
        f.setOwnerId(a.getOwner().getId());
        f.setContour(a.getContour().name());
        f.setExternalUrl(a.getExternalUrl());
        f.setLineId(a.getLine() == null ? null : a.getLine().getId());
        f.setReviewPeriodDays(String.valueOf(a.getReviewPeriodDays()));
        List<String> models = new ArrayList<>();
        artifacts.findSkusByArtifactIds(List.of(id)).forEach(r -> models.add(((Sku) r[1]).getModel()));
        f.setSkus(String.join(", ", models));
        f.setTagIds(artifacts.findTags(id).stream().map(Tag::getId).collect(Collectors.toCollection(ArrayList::new)));
        return f;
    }

    /** Где уже лежит файл с таким же содержимым (для предупреждения о дубле, SPEC.md 5.5). */
    @Transactional(readOnly = true)
    public List<ArtifactFile> findDuplicates(String sha256) {
        return files.findBySha256(sha256);
    }

    @Transactional(readOnly = true)
    public ArtifactFile fileForDownload(Long fileId, HubUserDetails user) {
        ArtifactFile f = files.findForDownload(fileId).orElseThrow(() -> new ArtifactNotFoundException(fileId));
        Artifact a = f.getVersion().getArtifact();
        if (a.isDeleted() || !permissions.canView(user, a)) {
            throw new AccessDeniedException("Файл недоступен");
        }
        return f;
    }

    // ------------------------------------------------------------------ создание

    @Transactional
    public Artifact create(ArtifactDraft d, List<IncomingFile> incoming, String comment, HubUserDetails user) {
        requireEdit(user);
        Map<String, String> errors = new LinkedHashMap<>();
        if (d.title() == null || d.title().isBlank()) {
            errors.put("title", "validation.required");
        }
        if (d.type() == null) {
            errors.put("typeId", "validation.required");
        }
        if (d.owner() == null) {
            errors.put("ownerId", "validation.required");
        }
        boolean hasUrl = d.externalUrl() != null && !d.externalUrl().isBlank();
        if ((incoming == null || incoming.isEmpty()) && !hasUrl) {
            errors.put("files", "validation.artifact.fileOrUrl");
        }
        if (hasUrl && !validUrl(d.externalUrl())) {
            errors.put("externalUrl", "validation.url.invalid");
        }
        if (incoming != null) {
            validateFiles(incoming, errors);
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }

        Artifact a = new Artifact(d.title().trim(), d.type(), d.language() == null ? Language.NONE : d.language(),
                d.status() == null ? ArtifactStatus.ACTUAL : d.status(), d.owner(),
                d.contour() == null ? d.type().getDefaultContour() : d.contour(), user.getUsername());
        a.setDescription(blankToNull(d.description()));
        a.setExternalUrl(hasUrl ? d.externalUrl().trim() : null);
        a.setLine(d.line());
        a.setSourceKey(d.sourceKey());
        a.getSkus().addAll(d.skus());
        a.getTags().addAll(d.tags());
        artifacts.save(a);

        int versionNo = 0;
        List<String> stored = new ArrayList<>();
        try {
            if (incoming != null && !incoming.isEmpty()) {
                versionNo = addVersionInternal(a, incoming, comment, user.getUsername(), stored, new ArrayList<>());
            }
        } catch (IOException | RuntimeException e) {
            cleanup(stored);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
        audit.record(ENTITY, a.getId(), AuditAction.CREATE, null, null, a.getTitle(), user.getUsername(), null);
        refreshSkus(a.getSkus().stream().map(Sku::getId).toList());
        events.publishEvent(new ArtifactUpdated(a.getId(), versionNo, true, user.getUsername()));
        return a;
    }

    // ------------------------------------------------------------------ версии

    /** Новая версия: старая уходит в историю, lastReviewedAt обновляется, публикуется ArtifactUpdated. */
    public record AddedVersion(int versionNo, List<String> duplicateWarnings) {
    }

    @Transactional
    public AddedVersion addVersion(Long id, List<IncomingFile> incoming, String comment, HubUserDetails user) {
        requireEdit(user);
        Artifact a = load(id);
        Map<String, String> errors = new LinkedHashMap<>();
        if (incoming == null || incoming.isEmpty()) {
            errors.put("files", "validation.required");
        } else {
            validateFiles(incoming, errors);
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        List<String> stored = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        int no;
        try {
            no = addVersionInternal(a, incoming, comment, user.getUsername(), stored, duplicates);
        } catch (IOException | RuntimeException e) {
            cleanup(stored);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
        if (a.getStatus() == ArtifactStatus.OUTDATED) {
            audit.record(ENTITY, id, AuditAction.STATUS_CHANGE, "status", a.getStatus(), ArtifactStatus.ACTUAL,
                    user.getUsername(), null);
            a.setStatus(ArtifactStatus.ACTUAL);
        }
        audit.record(ENTITY, id, AuditAction.UPDATE, "version", no - 1, no, user.getUsername(), blankToNull(comment));
        refreshSkus(artifacts.findSkuIds(id));
        events.publishEvent(new ArtifactUpdated(id, no, false, user.getUsername()));
        return new AddedVersion(no, duplicates);
    }

    private int addVersionInternal(Artifact a, List<IncomingFile> incoming, String comment, String login,
                                   List<String> stored, List<String> duplicates) throws IOException {
        int no = versions.maxVersionNo(a.getId()) + 1;
        ArtifactVersion v = versions.save(new ArtifactVersion(a, no, blankToNull(comment), login));
        for (IncomingFile f : incoming) {
            FileStorage.StoredFile sf;
            try (InputStream in = f.source().open()) {
                sf = storage.store(in);
            }
            stored.add(sf.key());
            files.findBySha256(sf.sha256()).stream().map(x -> x.getVersion().getArtifact()).distinct()
                    .forEach(other -> duplicates.add(f.fileName() + ": такой файл уже есть в материале «"
                            + other.getTitle() + "» (№" + other.getId() + ")"));
            files.save(new ArtifactFile(v, f.fileName(), sf.key(), sf.size(), FileTypes.mime(f.fileName()), sf.sha256(),
                    f.role()));
        }
        a.setCurrentVersion(v);
        a.markReviewed(LocalDateTime.now());
        return no;
    }

    // ------------------------------------------------------------------ правка

    @Transactional
    public void update(Long id, ArtifactForm form, HubUserDetails user) {
        requireEdit(user);
        Artifact a = load(id);
        Map<String, String> errors = new LinkedHashMap<>();
        String title = blankToNull(form.getTitle());
        if (title == null) {
            errors.put("title", "validation.required");
        }
        ArtifactType type = form.getTypeId() == null ? null : types.byId(form.getTypeId()).orElse(null);
        if (type == null) {
            errors.put("typeId", "validation.required");
        }
        User owner = form.getOwnerId() == null ? null : users.findById(form.getOwnerId()).orElse(null);
        if (owner == null) {
            errors.put("ownerId", "validation.required");
        }
        Language language = parse(Language.class, form.getLanguage());
        if (language == null) {
            errors.put("language", "validation.required");
        }
        ArtifactStatus status = parse(ArtifactStatus.class, form.getStatus());
        if (status == null) {
            errors.put("status", "validation.required");
        }
        Contour contour = parse(Contour.class, form.getContour());
        if (contour == null) {
            errors.put("contour", "validation.required");
        }
        String url = blankToNull(form.getExternalUrl());
        if (url != null && !validUrl(url)) {
            errors.put("externalUrl", "validation.url.invalid");
        }
        int period = a.getReviewPeriodDays();
        String periodText = blankToNull(form.getReviewPeriodDays());
        if (periodText != null) {
            try {
                period = Integer.parseInt(periodText);
                if (period < 1) {
                    errors.put("reviewPeriodDays", "validation.number.invalid");
                }
            } catch (NumberFormatException e) {
                errors.put("reviewPeriodDays", "validation.number.invalid");
            }
        }
        List<Sku> newSkus = resolveSkus(form.getSkus(), errors);
        ProductLine line = form.getLineId() == null ? null : lines.findById(form.getLineId()).orElse(null);
        List<Tag> newTags = form.getTagIds() == null || form.getTagIds().isEmpty() ? List.of()
                : tags.findAllById(form.getTagIds());
        if (url == null && a.getCurrentVersion() == null) {
            errors.put("externalUrl", "validation.artifact.fileOrUrl");
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }

        String login = user.getUsername();
        change(a, "title", a.getTitle(), title, login, () -> a.setTitle(title));
        change(a, "description", a.getDescription(), blankToNull(form.getDescription()), login,
                () -> a.setDescription(blankToNull(form.getDescription())));
        change(a, "type", a.getType().getName(), type.getName(), login, () -> a.setType(type));
        change(a, "language", a.getLanguage(), language, login, () -> a.setLanguage(language));
        change(a, "status", a.getStatus(), status, login, () -> a.setStatus(status));
        change(a, "owner", a.getOwner().getLogin(), owner.getLogin(), login, () -> a.setOwner(owner));
        change(a, "contour", a.getContour(), contour, login, () -> a.setContour(contour));
        change(a, "externalUrl", a.getExternalUrl(), url, login, () -> a.setExternalUrl(url));
        change(a, "line", a.getLine() == null ? null : a.getLine().getCode(), line == null ? null : line.getCode(),
                login, () -> a.setLine(line));
        int finalPeriod = period;
        change(a, "reviewPeriodDays", a.getReviewPeriodDays(), period, login, () -> a.changeReviewPeriod(finalPeriod));

        Set<Long> oldSkuIds = a.getSkus().stream().map(Sku::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> newSkuIds = newSkus.stream().map(Sku::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!oldSkuIds.equals(newSkuIds)) {
            audit.record(ENTITY, id, AuditAction.UPDATE, "skus",
                    a.getSkus().stream().map(Sku::getModel).sorted().collect(Collectors.joining(", ")),
                    newSkus.stream().map(Sku::getModel).sorted().collect(Collectors.joining(", ")), login, null);
            a.getSkus().clear();
            a.getSkus().addAll(newSkus);
        }
        Set<Long> oldTagIds = a.getTags().stream().map(Tag::getId).collect(Collectors.toSet());
        Set<Long> newTagIds = newTags.stream().map(Tag::getId).collect(Collectors.toSet());
        if (!oldTagIds.equals(newTagIds)) {
            audit.record(ENTITY, id, AuditAction.UPDATE, "tags",
                    a.getTags().stream().map(Tag::getValue).sorted().collect(Collectors.joining(", ")),
                    newTags.stream().map(Tag::getValue).sorted().collect(Collectors.joining(", ")), login, null);
            a.getTags().clear();
            a.getTags().addAll(newTags);
        }
        a.setUpdatedAt(LocalDateTime.now());
        Set<Long> affected = new LinkedHashSet<>(oldSkuIds);
        affected.addAll(newSkuIds);
        artifacts.flush();
        refreshSkus(affected);
    }

    @Transactional
    public void confirmActual(Long id, HubUserDetails user) {
        requireEdit(user);
        Artifact a = load(id);
        LocalDateTime before = a.getLastReviewedAt();
        a.markReviewed(LocalDateTime.now());
        audit.record(ENTITY, id, AuditAction.UPDATE, "lastReviewedAt", before, a.getLastReviewedAt(),
                user.getUsername(), "подтверждена актуальность");
    }

    @Transactional
    public void delete(Long id, HubUserDetails user) {
        requireEdit(user);
        Artifact a = load(id);
        a.setDeleted(true);
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ENTITY, id, AuditAction.DELETE, null, a.getTitle(), null, user.getUsername(), null);
        refreshSkus(artifacts.findSkuIds(id));
    }

    // ------------------------------------------------------------------ массовые действия (PRODUCT, ADMIN)

    public enum BulkAction {
        STATUS, ADD_TAG, ADD_SKU, DELETE
    }

    @Transactional
    public int bulk(Collection<Long> ids, BulkAction action, String value, HubUserDetails user) {
        if (!permissions.canManage(user)) {
            throw new AccessDeniedException("Массовые действия доступны только PRODUCT и ADMIN");
        }
        if (ids == null || ids.isEmpty() || value == null || value.isBlank()) {
            throw new FormValidationException(Map.of("bulk", "validation.required"));
        }
        List<Artifact> list = artifacts.findAllById(ids).stream().filter(a -> !a.isDeleted()).toList();
        Set<Long> affected = new LinkedHashSet<>();
        for (Artifact a : list) {
            affected.addAll(artifacts.findSkuIds(a.getId()));
            switch (action) {
                case STATUS -> {
                    ArtifactStatus st = parse(ArtifactStatus.class, value);
                    if (st == null) {
                        throw new FormValidationException(Map.of("bulk", "validation.required"));
                    }
                    change(a, "status", a.getStatus(), st, user.getUsername(), () -> a.setStatus(st));
                }
                case ADD_TAG -> {
                    Tag tag = tags.findById(Long.valueOf(value)).orElseThrow();
                    if (a.getTags().add(tag)) {
                        audit.record(ENTITY, a.getId(), AuditAction.UPDATE, "tags", null, tag.getValue(),
                                user.getUsername(), "массовое действие");
                    }
                }
                case DELETE -> {
                    // мягкое удаление: материал скрывается, запись и файлы остаются, в журнал пишется название
                    a.setDeleted(true);
                    audit.record(ENTITY, a.getId(), AuditAction.DELETE, null, a.getTitle(), null, user.getUsername(),
                            "массовое действие");
                }
                case ADD_SKU -> {
                    Map<String, String> errors = new LinkedHashMap<>();
                    List<Sku> found = resolveSkus(value, errors);
                    if (!errors.isEmpty()) {
                        throw new FormValidationException(Map.of("bulk", errors.values().iterator().next()));
                    }
                    for (Sku s : found) {
                        if (a.getSkus().add(s)) {
                            affected.add(s.getId());
                            audit.record(ENTITY, a.getId(), AuditAction.UPDATE, "skus", null, s.getModel(),
                                    user.getUsername(), "массовое действие");
                        }
                    }
                }
            }
            a.setUpdatedAt(LocalDateTime.now());
        }
        artifacts.flush();
        refreshSkus(affected);
        return list.size();
    }

    // ------------------------------------------------------------------ вспомогательное

    /** Артикулы через запятую или перевод строки, сопоставление по нормализованному артикулу. */
    public List<Sku> resolveSkus(String text, Map<String, String> errors) {
        List<Sku> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        List<String> missing = new ArrayList<>();
        for (String part : text.split("[,;\\n]")) {
            String model = part.trim();
            if (model.isEmpty()) {
                continue;
            }
            skus.findByModelNormalized(ModelNormalizer.normalize(model)).filter(s -> !s.isDeleted())
                    .ifPresentOrElse(s -> {
                        if (!result.contains(s)) {
                            result.add(s);
                        }
                    }, () -> missing.add(model));
        }
        if (!missing.isEmpty()) {
            errors.put("skus", "validation.sku.notFound");
        }
        return result;
    }

    private void validateFiles(List<IncomingFile> incoming, Map<String, String> errors) {
        for (IncomingFile f : incoming) {
            String ext = FileTypes.extension(f.fileName());
            if (!fileProps.allowedExtensions().contains(ext)) {
                errors.put("files", "validation.file.extension");
                return;
            }
            try (InputStream in = f.source().open()) {
                if (!FileTypes.looksValid(ext, in.readNBytes(1024))) {
                    errors.put("files", "validation.file.content");
                    return;
                }
            } catch (IOException e) {
                errors.put("files", "validation.file.unreadable");
                return;
            }
        }
    }

    private void cleanup(List<String> storedKeys) {
        for (String key : storedKeys) {
            try {
                storage.delete(key);
            } catch (IOException e) {
                log.warn("Не удалось удалить файл {} после ошибки", key, e);
            }
        }
    }

    private void refreshSkus(Collection<Long> skuIds) {
        for (Long skuId : skuIds) {
            skuService.refreshCompleteness(skuId);
        }
    }

    private void change(Artifact a, String field, Object oldValue, Object newValue, String login, Runnable setter) {
        if (Objects.equals(norm(oldValue), norm(newValue))) {
            return;
        }
        AuditAction action = field.equals("status") ? AuditAction.STATUS_CHANGE : AuditAction.UPDATE;
        audit.record(ENTITY, a.getId(), action, field, oldValue, newValue, login, null);
        setter.run();
    }

    private static Object norm(Object o) {
        return o instanceof String s && s.isBlank() ? null : o;
    }

    private Artifact load(Long id) {
        return artifacts.findCardById(id).orElseThrow(() -> new ArtifactNotFoundException(id));
    }

    private void requireEdit(HubUserDetails user) {
        if (!permissions.canEdit(user)) {
            throw new AccessDeniedException("Нет прав на правку артефактов");
        }
    }

    private static boolean validUrl(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("https://") || u.startsWith("http://");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
