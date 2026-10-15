package ru.eltex.producthub.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditLogRepository;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecValue;
import ru.eltex.producthub.sku.repo.ProductLineRepository;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.repo.SpecValueRepository;
import ru.eltex.producthub.sku.service.LifecycleDate;
import ru.eltex.producthub.sku.service.ModelNormalizer;
import ru.eltex.producthub.sku.service.ReferenceDataService;
import ru.eltex.producthub.sku.service.SkuPermissions;
import ru.eltex.producthub.sku.service.SkuService;

/**
 * Импорт SKU из seed/ (SPEC.md 5.11, п. 1 и 2). Файлы читаются как есть и не изменяются.
 * Повторный запуск идемпотентен: SKU сопоставляются по нормализованному артикулу, ручные правки не затираются.
 */
@Service
public class SeedImportService {

    public static final String KIND = "SKU_SEED";
    static final String REGISTRY_FILE = "lifecycle_registry_wifi.xlsx";
    static final String SITE_FILE = "site_catalog_ru_en.xlsx";
    private static final Logger log = LoggerFactory.getLogger(SeedImportService.class);
    private static final Pattern LATIN_PREFIX = Pattern.compile("^[A-Za-z]+");

    private final ImportProperties props;
    private final SkuRepository skus;
    private final ProductLineRepository lines;
    private final UserRepository users;
    private final SpecValueRepository specValues;
    private final AuditService audit;
    private final AuditLogRepository auditLog;
    private final ReferenceDataService reference;
    private final ru.eltex.producthub.sku.service.SkuService skuService;
    private final SkuPermissions permissions;
    private final ImportRunRepository runs;
    private final int reviewPeriodDays;
    private final ArtifactTypeSeedImporter typeImporter;
    private final ru.eltex.producthub.sku.service.SkuOwners skuOwners;

    public SeedImportService(ImportProperties props, SkuRepository skus, ProductLineRepository lines,
                             UserRepository users, SpecValueRepository specValues, AuditService audit,
                             AuditLogRepository auditLog, ReferenceDataService reference,
                             ru.eltex.producthub.sku.service.SkuService skuService, SkuPermissions permissions,
                             ImportRunRepository runs,
                             @org.springframework.beans.factory.annotation.Value("${producthub.review-period-days:90}")
                             int reviewPeriodDays, ArtifactTypeSeedImporter typeImporter,
                             ru.eltex.producthub.sku.service.SkuOwners skuOwners) {
        this.props = props;
        this.skuOwners = skuOwners;
        this.skus = skus;
        this.lines = lines;
        this.users = users;
        this.specValues = specValues;
        this.audit = audit;
        this.auditLog = auditLog;
        this.reference = reference;
        this.skuService = skuService;
        this.permissions = permissions;
        this.runs = runs;
        this.reviewPeriodDays = reviewPeriodDays;
        this.typeImporter = typeImporter;
    }

    /** Запуск из интерфейса: только ADMIN. */
    @Transactional
    public ImportRun run(HubUserDetails user) {
        if (!permissions.canImport(user)) {
            throw new AccessDeniedException("Импорт доступен только администратору");
        }
        return execute(user.getUsername());
    }

    /** Запуск без проверки прав: первый старт приложения и тесты. */
    @Transactional
    public ImportRun execute(String actor) {
        return execute(actor, Path.of(props.seedDir()));
    }

    @Transactional
    public ImportRun execute(String actor, Path dir) {
        ImportRun run = new ImportRun(KIND, actor);
        Stats stats = new Stats();
        try {
            doImport(dir, stats);
        } catch (IOException | RuntimeException e) {
            log.error("Импорт не выполнен", e);
            stats.error("Импорт остановлен: " + e.getMessage());
        }
        reference.evictAll();
        stats.lines.add(0, "Создано: " + stats.created + ", обновлено: " + stats.updated + ", пропущено: "
                + stats.skipped + ", предупреждений: " + stats.warnings + ", ошибок: " + stats.errors);
        run.finish(stats.created, stats.updated, stats.skipped, stats.errors, String.join("\n", stats.lines));
        runs.save(run);
        log.info("Импорт {}: {}", KIND, stats.lines.get(0));
        return run;
    }

    @Transactional(readOnly = true)
    public List<ImportRun> latestRuns(int limit) {
        return runs.findLatest(PageRequest.of(0, limit));
    }

    // ------------------------------------------------------------------ основной проход

    private void doImport(Path dir, Stats stats) throws IOException {
        ExcelTable registry = ExcelTable.read(dir.resolve(REGISTRY_FILE), null, "SKU", "Статус ЖЦ");
        ExcelTable site = ExcelTable.read(dir.resolve(SITE_FILE), "Сайты", "Раздел", "Модель");

        Map<String, RegistryRow> registryByKey = readRegistry(registry, stats);

        Map<String, Sku> byKey = new HashMap<>();
        skus.findAll().forEach(s -> byKey.put(s.getModelNormalized(), s));
        Map<String, ProductLine> lineByCode = new HashMap<>();
        lines.findAll().forEach(l -> lineByCode.put(l.getCode(), l));
        Set<String> manual = new HashSet<>();
        for (Object[] row : auditLog.findManuallyEditedFields(SkuService.ENTITY, AuditService.IMPORT_USER)) {
            manual.add(row[0] + ":" + row[1]);
        }
        User defaultOwner = users.findByLoginAndActiveTrue(props.defaultOwner())
                .orElseThrow(() -> new IllegalStateException("Не найден владелец по умолчанию: " + props.defaultOwner()));

        Context ctx = new Context(stats, byKey, lineByCode, manual, defaultOwner);

        // 1. Каталог сайтов: все SKU компании
        Set<String> seenSite = new HashSet<>();
        for (int i = 0; i < site.size(); i++) {
            String model = site.text(i, "Модель");
            if (model == null) {
                stats.skipped++;
                stats.warn("Каталог сайтов, строка " + site.rowNumber(i) + ": нет артикула, строка пропущена");
                continue;
            }
            String key = ModelNormalizer.normalize(model);
            String where = "Каталог сайтов, строка " + site.rowNumber(i) + " (" + model + ")";
            if (!seenSite.add(key)) {
                stats.skipped++;
                stats.warn(where + ": дубль артикула, использована первая строка");
                continue;
            }
            SiteRow row = new SiteRow(model, site.text(i, "Раздел"), site.text(i, "Подраздел"),
                    site.text(i, "Статус доступности"), site.text(i, "Section"), site.text(i, "Subsection"),
                    site.text(i, "Status"));
            if (!LATIN_PREFIX.matcher(model).find()) {
                stats.warn(where + ": название не похоже на артикул (ПО или система), импортировано как есть");
            }
            upsertFromSite(ctx, key, row, registryByKey.get(key), where);
        }

        // 2. Реестр ЖЦ: SKU, которых нет на сайтах, и поля жизненного цикла
        Set<String> touched = new HashSet<>();
        for (RegistryRow r : registryByKey.values()) {
            Sku sku = ctx.byKey.get(r.key);
            String where = "Реестр ЖЦ, строка " + r.rowNumber + " (" + r.model + ")";
            if (sku == null) {
                stats.warn(where + ": нет в каталоге сайтов, SKU создан из реестра");
                sku = createSku(ctx, r.key, r.model, r, null, where);
                stats.created++;
            }
            if (applyRegistry(ctx, sku, r, where)) {
                touched.add(r.key);
            }
        }
        // 3. Преемники (после создания всех SKU)
        for (RegistryRow r : registryByKey.values()) {
            if (r.successor == null) {
                continue;
            }
            Sku sku = ctx.byKey.get(r.key);
            Sku successor = ctx.byKey.get(ModelNormalizer.normalize(r.successor));
            String where = "Реестр ЖЦ, строка " + r.rowNumber + " (" + r.model + ")";
            if (successor == null) {
                stats.warn(where + ": преемник \"" + r.successor + "\" не найден в базе, поле не заполнено");
            } else if (sku != null && !successor.getId().equals(sku.getId())
                    && change(ctx, sku, "successor", sku.getSuccessor() == null ? null : sku.getSuccessor().getModel(),
                    successor.getModel(), () -> sku.setSuccessor(successor))) {
                touched.add(r.key);
            }
        }

        // 4. Заполненность и счетчики
        for (Sku sku : ctx.byKey.values()) {
            if (ctx.createdKeys.contains(sku.getModelNormalized()) || ctx.changed.contains(sku.getId())) {
                List<SpecValue> values = ctx.createdKeys.contains(sku.getModelNormalized())
                        ? List.of() : specValues.findBySkuId(sku.getId());
                sku.setCompleteness(skuService.completenessFor(sku, values));
            }
        }
        Set<String> processed = new HashSet<>(seenSite);
        processed.addAll(registryByKey.keySet());
        List<String> typeLog = new ArrayList<>();
        int types = typeImporter.importTypes(dir, typeLog);
        typeLog.forEach(stats::warn);
        stats.lines.add("Типы артефактов: создано " + types);
        stats.created += types;
        stats.updated = ctx.changed.size() - countCreatedChanged(ctx);
        stats.skipped += Math.max(0, processed.size() - ctx.createdKeys.size() - stats.updated);
    }

    private int countCreatedChanged(Context ctx) {
        int n = 0;
        for (Sku s : ctx.byKey.values()) {
            if (ctx.createdKeys.contains(s.getModelNormalized()) && ctx.changed.contains(s.getId())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ реестр ЖЦ

    private Map<String, RegistryRow> readRegistry(ExcelTable t, Stats stats) {
        Map<String, RegistryRow> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < t.size(); i++) {
            String model = t.text(i, "SKU");
            if (model == null) {
                continue; // пустые строки реестра пропускаем молча (SPEC.md 5.11)
            }
            String where = "Реестр ЖЦ, строка " + t.rowNumber(i) + " (" + model + ")";
            String key = ModelNormalizer.normalize(model);
            if (result.containsKey(key)) {
                stats.skipped++;
                stats.warn(where + ": дубль артикула, использована первая строка");
                continue;
            }
            LifecycleStatus status = null;
            String statusText = t.text(i, "Статус ЖЦ");
            if (statusText != null) {
                status = mapRegistryStatus(statusText);
                if (status == null) {
                    stats.error(where + ": неизвестный статус ЖЦ \"" + statusText + "\"");
                }
            }
            RegistryRow r = new RegistryRow(key, model, t.rowNumber(i));
            r.line = t.text(i, "Линейка");
            r.chip = t.text(i, "Чип");
            r.status = status;
            r.ga = checkDate(t.text(i, "Дата запуска (GA)"), "Дата запуска (GA)", where, stats);
            r.eolAnnounce = checkDate(t.text(i, "Дата анонса EOL"), "Дата анонса EOL", where, stats);
            r.lastOrder = checkDate(t.text(i, "Last Order Date"), "Last Order Date", where, stats);
            r.lastShip = checkDate(t.text(i, "Last Ship Date"), "Last Ship Date", where, stats);
            r.endOfSupport = checkDate(t.text(i, "End of Support"), "End of Support", where, stats);
            r.successor = t.text(i, "Преемник / замена");
            r.owner = t.text(i, "Owner");
            r.lastReview = t.date(i, "Последнее ревью");
            r.nextReview = t.date(i, "Следующее ревью");
            result.put(key, r);
        }
        return result;
    }

    private String checkDate(String value, String column, String where, Stats stats) {
        String v = LifecycleDate.normalize(value);
        if (v != null && !LifecycleDate.isValid(v)) {
            stats.warn(where + ": в колонке \"" + column + "\" не дата и не квартал: \"" + value + "\", поле пропущено");
            return null;
        }
        return v;
    }

    private LifecycleStatus mapRegistryStatus(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "development" -> LifecycleStatus.DEVELOPMENT;
            case "new" -> LifecycleStatus.NEW;
            case "production" -> LifecycleStatus.PRODUCTION;
            case "eol" -> LifecycleStatus.EOL;
            default -> null;
        };
    }

    private boolean applyRegistry(Context ctx, Sku sku, RegistryRow r, String where) {
        boolean any = false;
        if (r.status != null) {
            any |= change(ctx, sku, "lifecycleStatus", sku.getLifecycleStatus(), r.status,
                    () -> sku.setLifecycleStatus(r.status));
        }
        any |= change(ctx, sku, "chip", sku.getChip(), r.chip, () -> sku.setChip(r.chip));
        any |= change(ctx, sku, "gaDate", sku.getGaDate(), r.ga, () -> sku.setGaDate(r.ga));
        any |= change(ctx, sku, "eolAnnounceDate", sku.getEolAnnounceDate(), r.eolAnnounce,
                () -> sku.setEolAnnounceDate(r.eolAnnounce));
        any |= change(ctx, sku, "lastOrderDate", sku.getLastOrderDate(), r.lastOrder,
                () -> sku.setLastOrderDate(r.lastOrder));
        any |= change(ctx, sku, "lastShipDate", sku.getLastShipDate(), r.lastShip,
                () -> sku.setLastShipDate(r.lastShip));
        any |= change(ctx, sku, "endOfSupportDate", sku.getEndOfSupportDate(), r.endOfSupport,
                () -> sku.setEndOfSupportDate(r.endOfSupport));
        any |= change(ctx, sku, "lastReviewDate", sku.getLastReviewDate(), r.lastReview,
                () -> sku.setLastReviewDate(r.lastReview));
        LocalDate next = r.nextReview != null ? r.nextReview
                : r.lastReview == null ? null : r.lastReview.plusDays(reviewPeriodDays);
        any |= change(ctx, sku, "nextReviewDate", sku.getNextReviewDate(), next, () -> sku.setNextReviewDate(next));
        // Владельцев повторный импорт не меняет: они распределены по семействам и правятся вручную
        return any;
    }

    // ------------------------------------------------------------------ каталог сайтов

    private void upsertFromSite(Context ctx, String key, SiteRow row, RegistryRow registry, String where) {
        Sku sku = ctx.byKey.get(key);
        if (sku == null) {
            sku = createSku(ctx, key, row.model, registry, row, where);
            ctx.stats.created++;
            return;
        }
        final Sku s = sku;
        change(ctx, s, "siteSectionRu", s.getSiteSectionRu(), row.sectionRu, () -> s.setSiteSectionRu(row.sectionRu));
        change(ctx, s, "siteSubsectionRu", s.getSiteSubsectionRu(), row.subsectionRu,
                () -> s.setSiteSubsectionRu(row.subsectionRu));
        change(ctx, s, "siteStatusRu", s.getSiteStatusRu(), row.statusRu, () -> s.setSiteStatusRu(row.statusRu));
        change(ctx, s, "siteSectionEn", s.getSiteSectionEn(), row.sectionEn, () -> s.setSiteSectionEn(row.sectionEn));
        change(ctx, s, "siteSubsectionEn", s.getSiteSubsectionEn(), row.subsectionEn,
                () -> s.setSiteSubsectionEn(row.subsectionEn));
        change(ctx, s, "siteStatusEn", s.getSiteStatusEn(), row.statusEn, () -> s.setSiteStatusEn(row.statusEn));
    }

    private Sku createSku(Context ctx, String key, String model, RegistryRow registry, SiteRow site, String where) {
        String lineCode = registry != null && registry.line != null ? registry.line.trim().toUpperCase(Locale.ROOT)
                : linePrefix(model);
        ProductLine line = lineByCode(ctx, lineCode, site);
        String category = categoryFor(lineCode, model, site);
        LifecycleStatus status = registry != null && registry.status != null ? registry.status
                : statusFromSite(site == null ? null : site.statusRu);
        User owner = registry != null ? ownerFor(ctx, registry.owner, where) : ctx.defaultOwner;
        Sku sku = new Sku(model, key, line, category, owner, status);
        skuOwners.assign(sku);
        if (site != null) {
            sku.setSiteSectionRu(site.sectionRu);
            sku.setSiteSubsectionRu(site.subsectionRu);
            sku.setSiteStatusRu(site.statusRu);
            sku.setSiteSectionEn(site.sectionEn);
            sku.setSiteSubsectionEn(site.subsectionEn);
            sku.setSiteStatusEn(site.statusEn);
        }
        skus.save(sku);
        ctx.byKey.put(key, sku);
        ctx.createdKeys.add(key);
        audit.record(SkuService.ENTITY, sku.getId(), AuditAction.CREATE, null, null, model, AuditService.IMPORT_USER,
                "импорт из seed");
        return sku;
    }

    private ProductLine lineByCode(Context ctx, String code, SiteRow site) {
        ProductLine line = ctx.lineByCode.get(code);
        if (line == null) {
            String family = registryFamily(code, site);
            line = lines.save(new ProductLine(code, code, family, ctx.defaultOwner, 1000 + ctx.lineByCode.size()));
            ctx.lineByCode.put(code, line);
        }
        return line;
    }

    private String registryFamily(String code, SiteRow site) {
        if (site == null) {
            return "Wi-Fi"; // реестр ЖЦ ведется по Wi-Fi
        }
        String family = props.find(props.familyBySection(), site.sectionRu);
        return family == null ? props.defaultFamily() : family;
    }

    private String linePrefix(String model) {
        Matcher m = LATIN_PREFIX.matcher(model);
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : props.otherLine();
    }

    private String categoryFor(String lineCode, String model, SiteRow site) {
        String byLine = props.find(props.categoryByLine(), lineCode);
        if (byLine != null) {
            return byLine;
        }
        String byPrefix = props.find(props.categoryByPrefix(), linePrefix(model));
        if (byPrefix != null) {
            return byPrefix;
        }
        if (site != null && site.subsectionRu != null) {
            return site.subsectionRu;
        }
        return "Прочее";
    }

    private LifecycleStatus statusFromSite(String statusRu) {
        if (statusRu == null) {
            return LifecycleStatus.PRODUCTION;
        }
        String s = statusRu.toLowerCase(Locale.ROOT);
        if (s.contains("в разработке")) {
            return LifecycleStatus.DEVELOPMENT;
        }
        if (s.contains("новинка")) {
            return LifecycleStatus.NEW;
        }
        if (s.startsWith("снято с производства")) {
            return LifecycleStatus.EOL;
        }
        return LifecycleStatus.PRODUCTION;
    }

    private User ownerFor(Context ctx, String ownerText, String where) {
        if (ownerText == null) {
            return ctx.defaultOwner;
        }
        String login = props.find(props.ownerAliases(), ownerText);
        if (login == null) {
            ctx.stats.warn(where + ": владелец \"" + ownerText + "\" не сопоставлен, назначен " + props.defaultOwner());
            return ctx.defaultOwner;
        }
        return users.findByLoginAndActiveTrue(login).orElse(ctx.defaultOwner);
    }

    /**
     * Меняет поле, если значение отличается и поле не правили вручную. Изменение пишется в аудит от имени import.
     */
    private boolean change(Context ctx, Sku sku, String field, Object oldValue, Object newValue, Runnable setter) {
        if (java.util.Objects.equals(oldValue, newValue) || (newValue == null && oldValue == null)) {
            return false;
        }
        if (newValue == null) {
            return false; // пустое значение источника не стирает данные в базе
        }
        if (ctx.manual.contains(sku.getId() + ":" + field)) {
            return false;
        }
        setter.run();
        sku.setUpdatedAt(LocalDateTime.now());
        if (!ctx.createdKeys.contains(sku.getModelNormalized())) {
            audit.record(SkuService.ENTITY, sku.getId(), AuditAction.UPDATE, field, oldValue, newValue,
                    AuditService.IMPORT_USER, "импорт из seed");
        }
        ctx.changed.add(sku.getId());
        return true;
    }

    // ------------------------------------------------------------------ служебные типы

    private static final class Context {
        final Stats stats;
        final Map<String, Sku> byKey;
        final Map<String, ProductLine> lineByCode;
        final Set<String> manual;
        final User defaultOwner;
        final Set<String> createdKeys = new HashSet<>();
        final Set<Long> changed = new HashSet<>();

        Context(Stats stats, Map<String, Sku> byKey, Map<String, ProductLine> lineByCode, Set<String> manual,
                User defaultOwner) {
            this.stats = stats;
            this.byKey = byKey;
            this.lineByCode = lineByCode;
            this.manual = manual;
            this.defaultOwner = defaultOwner;
        }
    }

    private static final class Stats {
        int created;
        int updated;
        int skipped;
        int errors;
        int warnings;
        final List<String> lines = new ArrayList<>();

        void warn(String message) {
            warnings++;
            lines.add("ПРЕДУПРЕЖДЕНИЕ: " + message);
        }

        void error(String message) {
            errors++;
            lines.add("ОШИБКА: " + message);
        }
    }

    private record SiteRow(String model, String sectionRu, String subsectionRu, String statusRu, String sectionEn,
                           String subsectionEn, String statusEn) {
    }

    private static final class RegistryRow {
        final String key;
        final String model;
        final int rowNumber;
        String line;
        String chip;
        LifecycleStatus status;
        String ga;
        String eolAnnounce;
        String lastOrder;
        String lastShip;
        String endOfSupport;
        String successor;
        String owner;
        LocalDate lastReview;
        LocalDate nextReview;

        RegistryRow(String key, String model, int rowNumber) {
            this.key = key;
            this.model = model;
            this.rowNumber = rowNumber;
        }
    }
}
