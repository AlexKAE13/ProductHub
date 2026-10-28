package ru.eltex.producthub.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.ArtifactType;
import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.repo.ArtifactRepository;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactPermissions;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.artifact.service.IncomingFile;
import ru.eltex.producthub.artifact.storage.FileProperties;
import ru.eltex.producthub.artifact.storage.FileTypes;
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
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.service.TagService;

/**
 * Импорт папки с файлами и загрузка файлов (SPEC.md 5.5, 5.11): сканирование, предпросмотр, правка, подтверждение.
 * Повторный импорт не создает дублей: файл с известным sha256 пропускается, измененный файл становится новой версией.
 */
@Service
public class FolderImportService {

    public static final String KIND = "FOLDER";
    public static final String KIND_DRY = "FOLDER_DRY";
    private static final Logger log = LoggerFactory.getLogger(FolderImportService.class);

    private final FilenameParser parser;
    private final FileImportProperties props;
    private final FileProperties fileProps;
    private final ImportProperties importProps;
    private final ImportPlanStore store;
    private final SkuRepository skus;
    private final ProductLineRepository lines;
    private final UserRepository users;
    private final TagService tagService;
    private final ArtifactTypeService types;
    private final ArtifactService artifactService;
    private final ArtifactRepository artifacts;
    private final ArtifactPermissions permissions;
    private final ImportRunRepository runs;
    private final FolderImportPolicy policy;

    public FolderImportService(FilenameParser parser, FileImportProperties props, FileProperties fileProps,
                               ImportProperties importProps, FolderImportPolicy policy, ImportPlanStore store, SkuRepository skus,
                               ProductLineRepository lines, UserRepository users, TagService tagService,
                               ArtifactTypeService types, ArtifactService artifactService,
                               ArtifactRepository artifacts, ArtifactPermissions permissions,
                               ImportRunRepository runs) {
        this.policy = policy;
        this.parser = parser;
        this.props = props;
        this.fileProps = fileProps;
        this.importProps = importProps;
        this.store = store;
        this.skus = skus;
        this.lines = lines;
        this.users = users;
        this.tagService = tagService;
        this.types = types;
        this.artifactService = artifactService;
        this.artifacts = artifacts;
        this.permissions = permissions;
        this.runs = runs;
    }

    // ------------------------------------------------------------------ сканирование

    /** Сканирует папку на сервере и готовит предпросмотр. Файлы не копируются. */
    @Transactional(readOnly = true)
    public ImportPlan scanFolder(String pathText, HubUserDetails user) {
        if (!permissions.canManage(user)) {
            throw new AccessDeniedException("Импорт папки доступен только PRODUCT и ADMIN");
        }
        Path root = pathText == null || pathText.isBlank() ? null : Path.of(pathText.trim());
        if (!policy.enabled()) {
            throw new AccessDeniedException("Импорт папки на этом сервере отключен");
        }
        if (root == null || !Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new FormValidationException(Map.of("path", "validation.path.invalid"));
        }
        if (!policy.allows(root)) {
            // не подсказываем, существует ли такая папка вне разрешенных: ответ одинаков
            throw new FormValidationException(Map.of("path", "validation.path.notAllowed"));
        }
        return buildPlan(root, root.toString(), null, user);
    }

    /** Сохраняет загруженные файлы во временную папку и готовит предпросмотр с автоподстановкой метаданных. */
    @Transactional(readOnly = true)
    public ImportPlan stageUploads(List<MultipartFile> uploads, HubUserDetails user) {
        if (!permissions.canEdit(user)) {
            throw new AccessDeniedException("Нет прав на загрузку артефактов");
        }
        List<MultipartFile> real = uploads == null ? List.of()
                : uploads.stream().filter(f -> !f.isEmpty()).toList();
        if (real.isEmpty()) {
            throw new FormValidationException(Map.of("files", "validation.required"));
        }
        try {
            Path staging = Path.of(fileProps.dir()).toAbsolutePath().resolve("staging").resolve(UUID.randomUUID().toString());
            Files.createDirectories(staging);
            for (MultipartFile f : real) {
                String name = Path.of(f.getOriginalFilename() == null ? "file" : f.getOriginalFilename())
                        .getFileName().toString();
                Path target = staging.resolve(name);
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return buildPlan(staging, "загрузка файлов (" + real.size() + ")", staging, user);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось принять файлы", e);
        }
    }

    private record Candidate(Path path, String relative, FilenameParser.Parsed parsed, long size) {
    }

    private ImportPlan buildPlan(Path root, String source, Path staging, HubUserDetails user) {
        ImportPlan plan = new ImportPlan(UUID.randomUUID().toString(), source, user.getUsername(), staging);
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        List<Path> all;
        try (Stream<Path> walk = Files.walk(root)) {
            all = walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new FormValidationException(Map.of("path", "validation.path.invalid"));
        }
        for (Path path : all) {
            String name = path.getFileName().toString();
            String rel = root.relativize(path).toString();
            long size;
            try {
                size = Files.size(path);
            } catch (IOException e) {
                plan.getSkipped().add(new SkippedFile(rel, "файл не читается"));
                continue;
            }
            String ext = FileTypes.extension(name);
            if (parser.isIgnored(name)) {
                plan.getSkipped().add(new SkippedFile(rel, "пропущен правилом ignore"));
            } else if (!fileProps.allowedExtensions().contains(ext)) {
                plan.getSkipped().add(new SkippedFile(rel, "расширение не разрешено"));
            } else if (size > fileProps.maxSizeBytes()) {
                plan.getSkipped().add(new SkippedFile(rel, "больше " + fileProps.maxSizeMb() + " МБ"));
            } else if (!FileTypes.looksValid(path)) {
                plan.getSkipped().add(new SkippedFile(rel, "содержимое не похоже на ." + ext));
            } else {
                var parsed = parser.parse(name);
                groups.computeIfAbsent(parsed.key(), k -> new ArrayList<>()).add(new Candidate(path, rel, parsed, size));
            }
        }

        Map<String, Sku> skuByKey = new HashMap<>();
        skus.findAllActiveWithOwner().forEach(s -> skuByKey.put(s.getModelNormalized(), s));
        Map<String, ProductLine> lineByKey = new HashMap<>();
        lines.findAll().forEach(l -> lineByKey.put(ModelNormalizer.normalize(l.getCode()), l));
        Long defaultOwner = defaultOwnerId(user);

        for (List<Candidate> group : groups.values()) {
            Candidate main = pickMain(group);
            Candidate preview = main.parsed().ext().equals("pdf") ? null
                    : group.stream().filter(c -> c != main && c.parsed().ext().equals("pdf")).findFirst().orElse(null);
            for (Candidate c : group) {
                if (c != main && c != preview) {
                    plan.getSkipped().add(new SkippedFile(c.relative(), "дубль имени, основным выбран " + main.relative()));
                }
            }
            plan.getRows().add(row(main, preview, skuByKey, lineByKey, defaultOwner));
        }
        store.put(plan);
        return plan;
    }

    /**
     * Загрузка из блока карточки SKU: строки без артикула привязываются к этому SKU, а всем строкам ставится тип блока
     * (например, battle card). Если материал с таким именем уже есть, но другого типа,
     * загрузка создает отдельный материал, а не новую версию чужого. Остальное правится в предпросмотре как обычно.
     */
    public void applyHints(ImportPlan plan, String sku, String typeName) {
        for (PlanRow row : plan.getRows()) {
            if (row.getAction() == PlanRow.Action.SKIP) {
                continue;
            }
            if (sku != null && (row.getSkus() == null || row.getSkus().isBlank())) {
                row.setSkus(sku);
                row.setSkuFound(true);
            }
            // загрузка из блока карточки SKU (например, "Добавить battle card") - явный выбор типа, он важнее догадки по имени файла;
            // тип по-прежнему можно поменять в предпросмотре
            if (typeName != null) {
                row.setType(typeName);
            }
            if (row.getAction() == PlanRow.Action.NEW_VERSION && row.getExistingId() != null) {
                Long wanted = types.byName(row.getType()).map(ArtifactType::getId).orElse(null);
                boolean sameType = artifacts.findTypeId(row.getExistingId()).map(id -> id.equals(wanted)).orElse(false);
                if (!sameType) {
                    row.setAction(PlanRow.Action.CREATE);
                    row.setExistingId(null);
                    row.setNote("материал с таким именем есть, но другого типа: создается отдельный");
                }
            }
        }
    }

    private Candidate pickMain(List<Candidate> group) {
        List<String> priority = props.mainPriority();
        return group.stream().min(Comparator.comparingInt((Candidate c) -> {
            int i = priority.indexOf(c.parsed().ext());
            if (i >= 0) {
                return i;
            }
            return c.parsed().ext().equals("pdf") ? 100 : 50;
        }).thenComparing(Candidate::relative)).orElseThrow();
    }

    private PlanRow row(Candidate main, Candidate preview, Map<String, Sku> skuByKey,
                        Map<String, ProductLine> lineByKey, Long defaultOwner) {
        var parsed = main.parsed();
        PlanRow row = new PlanRow();
        row.setKey(parsed.key());
        row.getFiles().add(planFile(main, FileRole.MAIN));
        if (preview != null) {
            row.getFiles().add(planFile(preview, FileRole.PREVIEW));
        }

        String codeKey = ModelNormalizer.normalize(parsed.body());
        Sku sku = skuByKey.get(codeKey);
        ProductLine line = sku == null ? lineByKey.get(codeKey) : null;
        FilenameParser.Match match = parser.match(parsed.body());

        String typeName = match != null ? match.type()
                : (sku != null || line != null ? props.productType() : props.otherType());
        ArtifactType type = types.byName(typeName).orElse(null);
        if (type == null) {
            row.getWarnings().add("тип \"" + typeName + "\" не найден в справочнике");
            type = types.byName(props.otherType()).orElse(null);
        }
        row.setType(type == null ? typeName : type.getName());
        row.setTitle(parser.title(parsed.body()));
        row.setLanguage(parsed.language().name());
        row.setStatus(ArtifactStatus.ACTUAL.name());
        row.setSkus(sku == null ? "" : sku.getModel());
        row.setSkuFound(sku != null);
        row.setLine(line == null ? "" : line.getCode());
        row.setOwnerId(sku != null ? sku.getOwner().getId() : defaultOwner);
        String contour = match != null && match.contour() != null ? match.contour()
                : type == null ? Contour.INTERNAL.name() : type.getDefaultContour().name();
        row.setContour(contour);

        List<String> tagValues = new ArrayList<>();
        if (match != null) {
            match.groups().forEach((group, text) -> {
                var category = switch (group) {
                    case "competitor" -> ru.eltex.producthub.tag.domain.TagCategory.COMPETITOR;
                    case "segment" -> ru.eltex.producthub.tag.domain.TagCategory.SEGMENT;
                    case "topic" -> ru.eltex.producthub.tag.domain.TagCategory.TOPIC;
                    default -> ru.eltex.producthub.tag.domain.TagCategory.MARKET;
                };
                tagService.resolve(text, category).ifPresentOrElse(t -> tagValues.add(t.getValue()),
                        () -> row.getWarnings().add("тег \"" + text + "\" (" + category + ") не найден в справочнике"));
            });
        }
        row.setTags(String.join(", ", tagValues));
        if (sku == null && line == null && match == null) {
            row.getWarnings().add("SKU и линейка по имени не найдены, привяжите материал вручную");
        }

        // повторный импорт: тот же файл - пропуск, измененный файл - новая версия
        List<ArtifactFile> same = artifactService.findDuplicates(row.getFiles().get(0).sha256());
        if (!same.isEmpty()) {
            Artifact a = same.get(0).getVersion().getArtifact();
            row.setAction(PlanRow.Action.SKIP);
            row.setExistingId(a.getId());
            row.setNote("файл уже загружен: " + a.getTitle() + " (№" + a.getId() + ")");
            row.setInclude(false);
        } else {
            List<Artifact> existing = artifacts.findBySourceKey(parsed.key());
            if (!existing.isEmpty()) {
                row.setAction(PlanRow.Action.NEW_VERSION);
                row.setExistingId(existing.get(0).getId());
                row.setNote("материал уже есть (№" + existing.get(0).getId() + "), файл изменился: будет новая версия");
            } else {
                row.setAction(PlanRow.Action.CREATE);
            }
            row.setInclude(true);
        }
        return row;
    }

    private PlanFile planFile(Candidate c, FileRole role) {
        return new PlanFile(c.path(), c.relative(), c.size(), sha256(c.path()), role);
    }

    private Long defaultOwnerId(HubUserDetails user) {
        if (user.getRoles().contains(ru.eltex.producthub.security.Role.PRODUCT)) {
            return user.getId();
        }
        return users.findByLoginAndActiveTrue(importProps.defaultOwner()).map(User::getId).orElse(user.getId());
    }

    static String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path); DigestInputStream din = new DigestInputStream(in,
                MessageDigest.getInstance("SHA-256"))) {
            din.transferTo(java.io.OutputStream.nullOutputStream());
            return java.util.HexFormat.of().formatHex(din.getMessageDigest().digest());
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Не удалось прочитать " + path, e);
        }
    }

    // ------------------------------------------------------------------ подтверждение

    public ImportRun confirm(String token, List<RowEdit> edits, boolean dryRun, HubUserDetails user) {
        ImportPlan plan = store.get(token);
        if (plan == null) {
            throw new FormValidationException(Map.of("plan", "validation.plan.expired"));
        }
        if (!plan.getCreatedBy().equals(user.getUsername())) {
            throw new AccessDeniedException("План импорта принадлежит другому пользователю");
        }
        boolean folder = plan.getStagingDir() == null;
        if (folder ? !permissions.canManage(user) : !permissions.canEdit(user)) {
            throw new AccessDeniedException("Нет прав на импорт");
        }
        for (int i = 0; i < plan.getRows().size() && edits != null && i < edits.size(); i++) {
            if (edits.get(i) != null) {
                plan.getRows().get(i).apply(edits.get(i));
            }
        }

        ImportRun run = new ImportRun(dryRun ? KIND_DRY : KIND, user.getUsername());
        List<String> lines = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        int warnings = 0;
        for (PlanRow row : plan.getRows()) {
            String label = row.getTitle() + " [" + row.getLanguage() + "]";
            if (row.getAction() == PlanRow.Action.SKIP || !row.isInclude()) {
                skipped++;
                lines.add("Пропущено: " + label + (row.getNote() != null ? " - " + row.getNote() : ""));
                continue;
            }
            try {
                Resolved r = resolve(row, lines);
                warnings += r.warnings;
                // файл другого типа никогда не становится новой версией чужого материала (battle card не затирает карточку продукта)
                if (row.getAction() == PlanRow.Action.NEW_VERSION && row.getExistingId() != null
                        && !artifacts.findTypeId(row.getExistingId()).map(id -> id.equals(r.type.getId())).orElse(false)) {
                    row.setAction(PlanRow.Action.CREATE);
                    row.setExistingId(null);
                    row.setNote("материал с таким именем есть, но другого типа: создается отдельный");
                }
                if (dryRun) {
                    lines.add((row.getAction() == PlanRow.Action.NEW_VERSION ? "Будет новая версия: " : "Будет создан: ")
                            + label + " (" + r.type.getName() + ")");
                    if (row.getAction() == PlanRow.Action.NEW_VERSION) {
                        updated++;
                    } else {
                        created++;
                    }
                    continue;
                }
                List<IncomingFile> incoming = row.getFiles().stream()
                        .map(f -> IncomingFile.of(f.path(), f.role())).toList();
                var dup = artifactService.findDuplicates(row.getFiles().get(0).sha256());
                if (!dup.isEmpty()) {
                    skipped++;
                    lines.add("Пропущено: " + label + " - файл уже загружен");
                    continue;
                }
                if (row.getAction() == PlanRow.Action.NEW_VERSION) {
                    var added = artifactService.addVersion(row.getExistingId(), incoming, "импорт: " + plan.getSource(), user);
                    updated++;
                    lines.add("Новая версия " + added.versionNo() + ": " + label);
                } else {
                    Artifact a = artifactService.create(new ArtifactDraft(row.getTitle(), row.getDescription(), r.type,
                            r.language, r.status, r.owner, r.contour, null, r.line, r.skus, r.tags, row.getKey()),
                            incoming, "импорт: " + plan.getSource(), user);
                    created++;
                    lines.add("Создан №" + a.getId() + ": " + label);
                }
            } catch (FormValidationException e) {
                errors++;
                lines.add("ОШИБКА: " + label + " - " + e.getErrors().values());
            } catch (RuntimeException e) {
                errors++;
                log.error("Ошибка импорта {}", label, e);
                lines.add("ОШИБКА: " + label + " - " + e.getMessage());
            }
        }
        lines.add(0, (dryRun ? "Проверка без записи. " : "") + "Создано: " + created + ", новых версий: " + updated
                + ", пропущено: " + skipped + ", предупреждений: " + warnings + ", ошибок: " + errors + ". Источник: "
                + plan.getSource());
        run.finish(created, updated, skipped, errors, String.join("\n", lines));
        run = runs.save(run);
        if (!dryRun) {
            store.remove(token);
        }
        return run;
    }

    private record Resolved(ArtifactType type, Language language, ArtifactStatus status, User owner, Contour contour,
                            ProductLine line, List<Sku> skus, List<Tag> tags, int warnings) {
    }

    private Resolved resolve(PlanRow row, List<String> lines) {
        Map<String, String> errors = new LinkedHashMap<>();
        ArtifactType type = types.byName(row.getType()).orElse(null);
        if (type == null) {
            errors.put("type", "validation.type.notFound");
        }
        Language language = parse(Language.class, row.getLanguage());
        ArtifactStatus status = parse(ArtifactStatus.class, row.getStatus());
        Contour contour = parse(Contour.class, row.getContour());
        if (language == null || status == null || contour == null) {
            errors.put("meta", "validation.required");
        }
        User owner = row.getOwnerId() == null ? null : users.findById(row.getOwnerId()).orElse(null);
        if (owner == null) {
            errors.put("owner", "validation.required");
        }
        ProductLine line = null;
        if (row.getLine() != null && !row.getLine().isBlank()) {
            line = lines().stream().filter(l -> l.getCode().equalsIgnoreCase(row.getLine().trim())).findFirst().orElse(null);
            if (line == null) {
                errors.put("line", "validation.line.notFound");
            }
        }
        List<Sku> skuList = artifactService.resolveSkus(row.getSkus(), errors);
        int warnings = 0;
        List<Tag> tagList = new ArrayList<>();
        if (row.getTags() != null) {
            for (String text : row.getTags().split("[,;]")) {
                if (text.isBlank()) {
                    continue;
                }
                var tag = tagService.resolve(text, null);
                if (tag.isPresent()) {
                    if (!tagList.contains(tag.get())) {
                        tagList.add(tag.get());
                    }
                } else {
                    warnings++;
                    lines.add("ПРЕДУПРЕЖДЕНИЕ: " + row.getTitle() + " - тег \"" + text.trim() + "\" не найден, пропущен");
                }
            }
        }
        if (row.getTitle() == null || row.getTitle().isBlank()) {
            errors.put("title", "validation.required");
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        return new Resolved(type, language, status, owner, contour, line, skuList, tagList, warnings);
    }

    private List<ProductLine> lines() {
        return lines.findAll();
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

    @Transactional(readOnly = true)
    public ImportRun run(Long id) {
        return runs.findById(id).orElseThrow();
    }

    /** Для тестов и страницы: список файлов плана в виде строк. */
    public static String describe(PlanRow row) {
        return row.getFiles().stream().map(f -> f.relativePath() + " (" + f.role() + ")").collect(Collectors.joining("; "));
    }

    public static LocalDate today() {
        return LocalDate.now();
    }
}
