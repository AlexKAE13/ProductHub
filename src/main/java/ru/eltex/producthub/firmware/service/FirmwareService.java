package ru.eltex.producthub.firmware.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.eltex.producthub.artifact.storage.FileStorage;
import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.firmware.domain.Firmware;
import ru.eltex.producthub.firmware.domain.FirmwareStatus;
import ru.eltex.producthub.firmware.event.FirmwarePublished;
import ru.eltex.producthub.firmware.repo.FirmwareQueryRepository;
import ru.eltex.producthub.firmware.repo.FirmwareRepository;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.service.SkuPermissions;
import ru.eltex.producthub.sku.service.SkuService;

/** Прошивки (SPEC.md 5.6): публикация, правка, отзыв, текущая версия SKU, событие FirmwarePublished. */
@Service
public class FirmwareService {

    public static final String ENTITY = "FIRMWARE";
    private static final int HISTORY_LIMIT = 50;

    private final FirmwareRepository repository;
    private final FirmwareQueryRepository queries;
    private final FileStorage storage;
    private final FirmwareProperties props;
    private final SkuService skuService;
    private final SkuPermissions skuPermissions;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public FirmwareService(FirmwareRepository repository, FirmwareQueryRepository queries, FileStorage storage,
                           FirmwareProperties props, SkuService skuService, SkuPermissions skuPermissions,
                           AuditService audit, ApplicationEventPublisher events) {
        this.repository = repository;
        this.queries = queries;
        this.storage = storage;
        this.props = props;
        this.skuService = skuService;
        this.skuPermissions = skuPermissions;
        this.audit = audit;
        this.events = events;
    }

    // ------------------------------------------------------------------ права

    /** Публиковать и править прошивки: PRODUCT, SERVICE_CENTER, RND, ADMIN (SPEC.md раздел 6). */
    public boolean canPublish(HubUserDetails user) {
        return user.getRoles().stream()
                .anyMatch(r -> r == Role.PRODUCT || r == Role.SERVICE_CENTER || r == Role.RND || r == Role.ADMIN);
    }

    private void requirePublish(HubUserDetails user) {
        if (!canPublish(user)) {
            throw new AccessDeniedException("Нет прав на публикацию прошивок");
        }
    }

    private boolean canView(HubUserDetails user, Firmware f) {
        return skuPermissions.isInternal(user) || f.isPublicRelease();
    }

    // ------------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public FirmwarePage search(FirmwareFilter filter, HubUserDetails user) {
        return queries.search(filter, !skuPermissions.isInternal(user));
    }

    /** Совместимые SKU для списка прошивок одним запросом. */
    @Transactional(readOnly = true)
    public Map<Long, List<Sku>> skusFor(List<Firmware> list) {
        Map<Long, List<Sku>> result = new HashMap<>();
        if (list.isEmpty()) {
            return result;
        }
        for (Object[] row : repository.findSkusByFirmwareIds(list.stream().map(Firmware::getId).toList())) {
            result.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Sku) row[1]);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public FirmwareDetail detail(Long id, HubUserDetails user) {
        Firmware f = load(id);
        if (!canView(user, f)) {
            throw new AccessDeniedException("Прошивка недоступна во внешнем контуре");
        }
        List<Sku> skus = new ArrayList<>();
        repository.findSkusByFirmwareIds(List.of(id)).forEach(r -> skus.add((Sku) r[1]));
        var history = skuPermissions.isInternal(user) ? audit.history(ENTITY, id, HISTORY_LIMIT)
                : List.<ru.eltex.producthub.common.AuditLog>of();
        return new FirmwareDetail(f, skus, history, canPublish(user));
    }

    @Transactional(readOnly = true)
    public FirmwareForm formFor(Long id, HubUserDetails user) {
        Firmware f = load(id);
        if (!canView(user, f)) {
            throw new AccessDeniedException("Прошивка недоступна");
        }
        FirmwareForm form = new FirmwareForm();
        form.setVersion(f.getVersion());
        form.setReleaseDate(f.getReleaseDate().toString());
        form.setStatus(f.getStatus().name());
        form.setReleaseNotes(f.getReleaseNotes());
        form.setExternalUrl(f.getExternalUrl());
        form.setPublicRelease(f.isPublicRelease());
        List<String> models = new ArrayList<>();
        repository.findSkusByFirmwareIds(List.of(id)).forEach(r -> models.add(((Sku) r[1]).getModel()));
        form.setSkus(String.join(", ", models));
        return form;
    }

    /** Блок 4 карточки SKU: текущая RELEASE-прошивка и остальные (кроме удаленных). */
    @Transactional(readOnly = true)
    public SkuFirmware forSku(Long skuId, HubUserDetails user) {
        boolean internal = skuPermissions.isInternal(user);
        List<Firmware> all = repository.findAllForSku(skuId).stream()
                .filter(f -> internal || f.isPublicRelease()).toList();
        Firmware current = all.stream().filter(f -> f.getStatus() == FirmwareStatus.RELEASE).findFirst().orElse(null);
        List<Firmware> others = all.stream().filter(f -> f != current).toList();
        return new SkuFirmware(current, others, canPublish(user));
    }

    /** Текущая прошивка для набора SKU (колонка каталога): один запрос на страницу. */
    @Transactional(readOnly = true)
    public Map<Long, Firmware> currentFor(java.util.Collection<Long> skuIds) {
        Map<Long, Firmware> result = new HashMap<>();
        if (skuIds.isEmpty()) {
            return result;
        }
        for (Object[] row : repository.findReleasesForSkus(skuIds)) {
            result.putIfAbsent((Long) row[0], (Firmware) row[1]);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Firmware fileForDownload(Long id, HubUserDetails user) {
        Firmware f = load(id);
        if (!canView(user, f)) {
            throw new AccessDeniedException("Прошивка недоступна");
        }
        if (!f.hasFile()) {
            throw new FirmwareNotFoundException(id);
        }
        return f;
    }

    // ------------------------------------------------------------------ публикация и правка

    public record Saved(Firmware firmware, List<String> warnings) {
    }

    @Transactional
    public Saved create(FirmwareForm form, MultipartFile file, HubUserDetails user) {
        requirePublish(user);
        Map<String, String> errors = new LinkedHashMap<>();
        Parsed p = parse(form, file, errors, true);
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        Firmware f = new Firmware(p.version, p.date, p.status, user.getUsername());
        f.setReleaseNotes(p.notes);
        f.setExternalUrl(p.url);
        f.setPublicRelease(form.isPublicRelease());
        f.getSkus().addAll(p.skus);
        List<String> stored = new ArrayList<>();
        try {
            storeFile(f, file, stored);
        } catch (IOException | RuntimeException e) {
            cleanup(stored);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
        repository.save(f);
        audit.record(ENTITY, f.getId(), AuditAction.CREATE, null, null, f.getVersion() + " (" + f.getStatus() + ")",
                user.getUsername(), null);
        List<String> warnings = duplicateWarnings(f, p.skus);
        if (f.getStatus() == FirmwareStatus.RELEASE) {
            publish(f, p.skus, user);
        }
        return new Saved(f, warnings);
    }

    @Transactional
    public Saved update(Long id, FirmwareForm form, MultipartFile file, HubUserDetails user) {
        requirePublish(user);
        Firmware f = load(id);
        Map<String, String> errors = new LinkedHashMap<>();
        Parsed p = parse(form, file, errors, false);
        if (p.url == null && !f.hasFile() && (file == null || file.isEmpty())) {
            errors.put("file", "validation.firmware.fileOrUrl");
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        String login = user.getUsername();
        Set<Long> oldSkus = new java.util.LinkedHashSet<>(repository.findSkuIds(id));
        FirmwareStatus oldStatus = f.getStatus();
        change(f, "version", f.getVersion(), p.version, login, () -> f.setVersion(p.version));
        change(f, "releaseDate", f.getReleaseDate(), p.date, login, () -> f.setReleaseDate(p.date));
        change(f, "status", f.getStatus(), p.status, login, () -> f.setStatus(p.status));
        change(f, "releaseNotes", f.getReleaseNotes(), p.notes, login, () -> f.setReleaseNotes(p.notes));
        change(f, "externalUrl", f.getExternalUrl(), p.url, login, () -> f.setExternalUrl(p.url));
        change(f, "publicRelease", f.isPublicRelease(), form.isPublicRelease(), login,
                () -> f.setPublicRelease(form.isPublicRelease()));
        Set<Long> newSkus = p.skus.stream().map(Sku::getId).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!oldSkus.equals(newSkus)) {
            audit.record(ENTITY, id, AuditAction.UPDATE, "skus",
                    f.getSkus().stream().map(Sku::getModel).sorted().collect(Collectors.joining(", ")),
                    p.skus.stream().map(Sku::getModel).sorted().collect(Collectors.joining(", ")), login, null);
            f.getSkus().clear();
            f.getSkus().addAll(p.skus);
        }
        List<String> stored = new ArrayList<>();
        if (file != null && !file.isEmpty()) {
            String oldName = f.getFileName();
            try {
                storeFile(f, file, stored);
            } catch (IOException | RuntimeException e) {
                cleanup(stored);
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
            audit.record(ENTITY, id, AuditAction.UPDATE, "file", oldName, f.getFileName(), login, null);
        }
        f.setUpdatedAt(LocalDateTime.now());
        repository.flush();
        if (oldStatus != FirmwareStatus.RELEASE && f.getStatus() == FirmwareStatus.RELEASE) {
            publish(f, p.skus, user);
        }
        return new Saved(f, duplicateWarnings(f, p.skus));
    }

    /** Быстрая смена статуса (например, отзыв прошивки). */
    @Transactional
    public void changeStatus(Long id, FirmwareStatus status, HubUserDetails user) {
        requirePublish(user);
        Firmware f = load(id);
        if (f.getStatus() == status) {
            return;
        }
        FirmwareStatus old = f.getStatus();
        audit.record(ENTITY, id, AuditAction.STATUS_CHANGE, "status", old, status, user.getUsername(), null);
        f.setStatus(status);
        f.setUpdatedAt(LocalDateTime.now());
        List<Sku> skus = new ArrayList<>();
        repository.findSkusByFirmwareIds(List.of(id)).forEach(r -> skus.add((Sku) r[1]));
        if (status == FirmwareStatus.RELEASE) {
            publish(f, skus, user);
        }
    }

    @Transactional
    public void delete(Long id, HubUserDetails user) {
        requirePublish(user);
        Firmware f = load(id);
        f.setDeleted(true);
        f.setUpdatedAt(LocalDateTime.now());
        audit.record(ENTITY, id, AuditAction.DELETE, null, f.getVersion(), null, user.getUsername(), null);
    }

    // ------------------------------------------------------------------ вспомогательное

    private record Parsed(String version, LocalDate date, FirmwareStatus status, String notes, String url,
                          List<Sku> skus) {
    }

    private Parsed parse(FirmwareForm form, MultipartFile file, Map<String, String> errors, boolean creating) {
        String version = blankToNull(form.getVersion());
        if (version == null) {
            errors.put("version", "validation.required");
        }
        LocalDate date = null;
        try {
            date = blankToNull(form.getReleaseDate()) == null ? LocalDate.now() : LocalDate.parse(form.getReleaseDate().trim());
        } catch (java.time.format.DateTimeParseException e) {
            errors.put("releaseDate", "validation.date.invalid");
        }
        FirmwareStatus status = null;
        try {
            status = FirmwareStatus.valueOf(form.getStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            errors.put("status", "validation.required");
        }
        String url = blankToNull(form.getExternalUrl());
        if (url != null && !(url.toLowerCase(Locale.ROOT).startsWith("http://") || url.toLowerCase(Locale.ROOT).startsWith("https://"))) {
            errors.put("externalUrl", "validation.url.invalid");
        }
        boolean hasFile = file != null && !file.isEmpty();
        if (creating && url == null && !hasFile) {
            errors.put("file", "validation.firmware.fileOrUrl");
        }
        if (hasFile && file.getSize() > props.maxSizeBytes()) {
            errors.put("file", "validation.firmware.size");
        }
        List<Sku> skus = skuService.resolveModels(form.getSkus(), errors, "skus");
        if (skus.isEmpty() && !errors.containsKey("skus")) {
            errors.put("skus", "validation.firmware.skuRequired");
        }
        return new Parsed(version, date, status, blankToNull(form.getReleaseNotes()), url, skus);
    }

    private void storeFile(Firmware f, MultipartFile file, List<String> stored) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        String name = file.getOriginalFilename() == null ? "firmware.bin"
                : java.nio.file.Path.of(file.getOriginalFilename()).getFileName().toString();
        try (InputStream in = file.getInputStream()) {
            FileStorage.StoredFile sf = storage.store(in);
            stored.add(sf.key());
            f.setFile(name, sf.key(), sf.size(), sf.sha256());
        }
    }

    private void cleanup(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (IOException ignored) {
                // файл остался сиротой, на целостность БД не влияет
            }
        }
    }

    /** Та же версия уже есть у этого SKU: сохраняем, но предупреждаем. */
    private List<String> duplicateWarnings(Firmware f, List<Sku> skus) {
        List<String> warnings = new ArrayList<>();
        for (Sku s : skus) {
            for (Firmware other : repository.findAllForSku(s.getId())) {
                if (!other.getId().equals(f.getId()) && other.getVersion().equalsIgnoreCase(f.getVersion())) {
                    warnings.add(s.getModel() + ": версия " + f.getVersion() + " уже есть (прошивка №" + other.getId() + ")");
                }
            }
        }
        return warnings;
    }

    private void publish(Firmware f, List<Sku> skus, HubUserDetails user) {
        events.publishEvent(new FirmwarePublished(f.getId(), f.getVersion(),
                skus.stream().map(Sku::getId).toList(), user.getUsername()));
    }

    private void change(Firmware f, String field, Object oldValue, Object newValue, String login, Runnable setter) {
        if (Objects.equals(norm(oldValue), norm(newValue))) {
            return;
        }
        audit.record(ENTITY, f.getId(), field.equals("status") ? AuditAction.STATUS_CHANGE : AuditAction.UPDATE, field,
                oldValue, newValue, login, null);
        setter.run();
    }

    private static Object norm(Object o) {
        return o instanceof String s && s.isBlank() ? null : o;
    }

    private Firmware load(Long id) {
        return repository.findActiveById(id).orElseThrow(() -> new FirmwareNotFoundException(id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
