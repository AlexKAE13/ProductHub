package ru.eltex.producthub.casestudy.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.artifact.domain.ArtifactFile;
import ru.eltex.producthub.artifact.domain.ArtifactStatus;
import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.domain.Language;
import ru.eltex.producthub.artifact.repo.ArtifactFileRepository;
import ru.eltex.producthub.artifact.service.ArtifactDraft;
import ru.eltex.producthub.artifact.service.ArtifactService;
import ru.eltex.producthub.artifact.service.ArtifactTypeService;
import ru.eltex.producthub.artifact.service.IncomingFile;
import ru.eltex.producthub.casestudy.domain.CaseStudy;
import ru.eltex.producthub.casestudy.repo.CaseQueryRepository;
import ru.eltex.producthub.casestudy.repo.CaseRepository;
import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.common.FormValidationException;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.Role;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.service.SkuPermissions;
import ru.eltex.producthub.sku.service.SkuService;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.repo.TagRepository;

/** Кейсы (SPEC.md 5.7). Создают и правят PRODUCT и ADMIN. Имя закрытого заказчика видят PRODUCT, HEAD и ADMIN. */
@Service
public class CaseService {

    public static final String ENTITY = "CASE";
    public static final String ATTACHMENT_TYPE = "Кейс / референс внедрения";
    private static final int HISTORY_LIMIT = 50;

    private final CaseRepository repository;
    private final CaseQueryRepository queries;
    private final SkuService skuService;
    private final SkuPermissions skuPermissions;
    private final TagRepository tags;
    private final UserRepository users;
    private final ArtifactService artifactService;
    private final ArtifactTypeService types;
    private final ArtifactFileRepository files;
    private final AuditService audit;

    public CaseService(CaseRepository repository, CaseQueryRepository queries, SkuService skuService,
                       SkuPermissions skuPermissions, TagRepository tags, UserRepository users,
                       ArtifactService artifactService, ArtifactTypeService types, ArtifactFileRepository files,
                       AuditService audit) {
        this.repository = repository;
        this.queries = queries;
        this.skuService = skuService;
        this.skuPermissions = skuPermissions;
        this.tags = tags;
        this.users = users;
        this.artifactService = artifactService;
        this.types = types;
        this.files = files;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ права

    public boolean canEdit(HubUserDetails user) {
        return has(user, Role.PRODUCT, Role.ADMIN);
    }

    /** Имя заказчика: открыто для всех, если customerVisible, иначе только PRODUCT, HEAD, ADMIN. */
    public boolean canSeeCustomer(HubUserDetails user, CaseStudy c) {
        return c.isCustomerVisible() || has(user, Role.PRODUCT, Role.HEAD, Role.ADMIN);
    }

    private boolean canView(HubUserDetails user, CaseStudy c) {
        return skuPermissions.isInternal(user) || c.isPublicationAllowed();
    }

    private static boolean has(HubUserDetails user, Role... roles) {
        for (Role r : roles) {
            if (user.getRoles().contains(r)) {
                return true;
            }
        }
        return false;
    }

    private void requireEdit(HubUserDetails user) {
        if (!canEdit(user)) {
            throw new AccessDeniedException("Кейсы создают и правят только PRODUCT и ADMIN");
        }
    }

    // ------------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public CasePage search(CaseFilter filter, HubUserDetails user) {
        return queries.search(filter, !skuPermissions.isInternal(user));
    }

    public record ListData(Map<Long, List<Sku>> skus, Map<Long, List<Tag>> tags) {
    }

    /** SKU и теги для списка кейсов двумя запросами. */
    @Transactional(readOnly = true)
    public ListData listData(List<CaseStudy> list) {
        Map<Long, List<Sku>> skus = new HashMap<>();
        Map<Long, List<Tag>> tagMap = new HashMap<>();
        if (!list.isEmpty()) {
            List<Long> ids = list.stream().map(CaseStudy::getId).toList();
            repository.findSkusByCaseIds(ids).forEach(r -> skus.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((Sku) r[1]));
            repository.findTagsByCaseIds(ids).forEach(r -> tagMap.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((Tag) r[1]));
        }
        return new ListData(skus, tagMap);
    }

    @Transactional(readOnly = true)
    public List<String> countries() {
        return repository.findCountries();
    }

    @Transactional(readOnly = true)
    public List<Integer> years() {
        return repository.findYears();
    }

    @Transactional(readOnly = true)
    public CaseDetail detail(Long id, HubUserDetails user) {
        CaseStudy c = load(id);
        if (!canView(user, c)) {
            throw new AccessDeniedException("Кейс недоступен во внешнем контуре");
        }
        List<Sku> skus = new ArrayList<>();
        repository.findSkusByCaseIds(List.of(id)).forEach(r -> skus.add((Sku) r[1]));
        List<CaseDetail.Attachment> attachments = attachments(id);
        boolean visible = canSeeCustomer(user, c);
        var history = skuPermissions.isInternal(user) ? audit.history(ENTITY, id, HISTORY_LIMIT)
                : List.<ru.eltex.producthub.common.AuditLog>of();
        return new CaseDetail(c, visible ? c.getCustomerName() : null,
                !visible && c.getCustomerName() != null && !c.getCustomerName().isBlank(), skus,
                repository.findTags(id), attachments, history, canEdit(user));
    }

    private List<CaseDetail.Attachment> attachments(Long id) {
        List<Artifact> list = repository.findAttachments(id);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ArtifactFile>> byVersion = files.findByVersionIds(
                list.stream().map(a -> a.getCurrentVersion().getId()).toList()).stream()
                .collect(Collectors.groupingBy(f -> f.getVersion().getId()));
        List<CaseDetail.Attachment> result = new ArrayList<>();
        for (Artifact a : list) {
            List<ArtifactFile> fs = byVersion.getOrDefault(a.getCurrentVersion().getId(), List.of());
            result.add(new CaseDetail.Attachment(a,
                    fs.stream().filter(f -> f.getRole() == FileRole.MAIN).findFirst().orElse(null),
                    fs.stream().filter(f -> f.getRole() == FileRole.PREVIEW).findFirst().orElse(null)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CaseForm formFor(Long id, HubUserDetails user) {
        requireEdit(user);
        CaseStudy c = load(id);
        CaseForm f = new CaseForm();
        f.setTitle(c.getTitle());
        f.setCountry(c.getCountry());
        f.setCity(c.getCity());
        f.setCustomerName(c.getCustomerName());
        f.setCustomerVisible(c.isCustomerVisible());
        f.setSummary(c.getSummary());
        f.setChallenge(c.getChallenge());
        f.setSolution(c.getSolution());
        f.setResults(c.getResults());
        f.setDeploymentSize(c.getDeploymentSize());
        f.setYear(c.getYear() == null ? null : c.getYear().toString());
        f.setOwnerId(c.getOwner().getId());
        f.setPublicationAllowed(c.isPublicationAllowed());
        List<String> models = new ArrayList<>();
        repository.findSkusByCaseIds(List.of(id)).forEach(r -> models.add(((Sku) r[1]).getModel()));
        f.setSkus(String.join(", ", models));
        repository.findTags(id).forEach(t -> f.getTagIds().add(t.getId()));
        return f;
    }

    /** Блок 7 карточки SKU: кейсы со страной, масштабом и тегами. */
    @Transactional(readOnly = true)
    public SkuCases forSku(Long skuId, HubUserDetails user) {
        boolean internal = skuPermissions.isInternal(user);
        List<CaseStudy> list = repository.findForSku(skuId).stream()
                .filter(c -> internal || c.isPublicationAllowed()).toList();
        Map<Long, List<Tag>> tagMap = new HashMap<>();
        if (!list.isEmpty()) {
            repository.findTagsByCaseIds(list.stream().map(CaseStudy::getId).toList())
                    .forEach(r -> tagMap.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((Tag) r[1]));
        }
        return new SkuCases(list, tagMap, canEdit(user));
    }

    // ------------------------------------------------------------------ создание и правка

    @Transactional
    public CaseStudy create(CaseForm form, HubUserDetails user) {
        requireEdit(user);
        Map<String, String> errors = new LinkedHashMap<>();
        Parsed p = parse(form, errors);
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        CaseStudy c = new CaseStudy(p.title, p.country, p.owner, user.getUsername());
        apply(c, form, p);
        c.getSkus().addAll(p.skus);
        c.getTags().addAll(p.tags);
        repository.save(c);
        audit.record(ENTITY, c.getId(), AuditAction.CREATE, null, null, c.getTitle(), user.getUsername(), null);
        return c;
    }

    @Transactional
    public void update(Long id, CaseForm form, HubUserDetails user) {
        requireEdit(user);
        CaseStudy c = load(id);
        Map<String, String> errors = new LinkedHashMap<>();
        Parsed p = parse(form, errors);
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        String login = user.getUsername();
        change(c, "title", c.getTitle(), p.title, login);
        change(c, "country", c.getCountry(), p.country, login);
        change(c, "city", c.getCity(), blank(form.getCity()), login);
        change(c, "customerName", c.getCustomerName(), blank(form.getCustomerName()), login);
        change(c, "customerVisible", c.isCustomerVisible(), form.isCustomerVisible(), login);
        change(c, "summary", c.getSummary(), blank(form.getSummary()), login);
        change(c, "challenge", c.getChallenge(), blank(form.getChallenge()), login);
        change(c, "solution", c.getSolution(), blank(form.getSolution()), login);
        change(c, "results", c.getResults(), blank(form.getResults()), login);
        change(c, "deploymentSize", c.getDeploymentSize(), blank(form.getDeploymentSize()), login);
        change(c, "year", c.getYear(), p.year, login);
        change(c, "owner", c.getOwner().getLogin(), p.owner.getLogin(), login);
        change(c, "publicationAllowed", c.isPublicationAllowed(), form.isPublicationAllowed(), login);
        Set<Long> oldSkus = c.getSkus().stream().map(Sku::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> newSkus = p.skus.stream().map(Sku::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!oldSkus.equals(newSkus)) {
            audit.record(ENTITY, id, AuditAction.UPDATE, "skus",
                    c.getSkus().stream().map(Sku::getModel).sorted().collect(Collectors.joining(", ")),
                    p.skus.stream().map(Sku::getModel).sorted().collect(Collectors.joining(", ")), login, null);
            c.getSkus().clear();
            c.getSkus().addAll(p.skus);
        }
        Set<Long> oldTags = c.getTags().stream().map(Tag::getId).collect(Collectors.toSet());
        Set<Long> newTags = p.tags.stream().map(Tag::getId).collect(Collectors.toSet());
        if (!oldTags.equals(newTags)) {
            audit.record(ENTITY, id, AuditAction.UPDATE, "tags",
                    c.getTags().stream().map(Tag::getValue).sorted().collect(Collectors.joining(", ")),
                    p.tags.stream().map(Tag::getValue).sorted().collect(Collectors.joining(", ")), login, null);
            c.getTags().clear();
            c.getTags().addAll(p.tags);
        }
        apply(c, form, p);
        c.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id, HubUserDetails user) {
        requireEdit(user);
        CaseStudy c = load(id);
        c.setDeleted(true);
        c.setUpdatedAt(LocalDateTime.now());
        audit.record(ENTITY, id, AuditAction.DELETE, null, c.getTitle(), null, user.getUsername(), null);
    }

    // ------------------------------------------------------------------ вложения

    /** Файлы кейса (PDF, фото) сохраняются как артефакты типа "Кейс / референс внедрения" и привязываются к кейсу. */
    @Transactional
    public int addAttachments(Long id, List<MultipartFile> uploads, HubUserDetails user) {
        requireEdit(user);
        CaseStudy c = load(id);
        List<MultipartFile> real = uploads == null ? List.of() : uploads.stream().filter(f -> !f.isEmpty()).toList();
        if (real.isEmpty()) {
            throw new FormValidationException(Map.of("files", "validation.required"));
        }
        var type = types.byName(ATTACHMENT_TYPE)
                .orElseThrow(() -> new IllegalStateException("Нет типа артефакта \"" + ATTACHMENT_TYPE + "\""));
        List<Sku> skus = new ArrayList<>(c.getSkus());
        for (MultipartFile file : real) {
            IncomingFile incoming = IncomingFile.of(file, FileRole.MAIN);
            Artifact a = artifactService.create(new ArtifactDraft(c.getTitle() + " - " + incoming.fileName(), null, type,
                    Language.NONE, ArtifactStatus.ACTUAL, c.getOwner(),
                    c.isPublicationAllowed() ? Contour.EXTERNAL : Contour.INTERNAL, null, null, skus, List.of(), null),
                    List.of(incoming), "вложение кейса №" + id, user);
            c.getArtifacts().add(a);
        }
        audit.record(ENTITY, id, AuditAction.UPDATE, "attachments", null, real.size() + " файл(ов)", user.getUsername(), null);
        return real.size();
    }

    @Transactional
    public void removeAttachment(Long caseId, Long artifactId, HubUserDetails user) {
        requireEdit(user);
        CaseStudy c = load(caseId);
        Artifact found = c.getArtifacts().stream().filter(a -> a.getId().equals(artifactId)).findFirst().orElse(null);
        if (found != null) {
            c.getArtifacts().remove(found);
            artifactService.delete(artifactId, user);
            audit.record(ENTITY, caseId, AuditAction.UPDATE, "attachments", found.getTitle(), null, user.getUsername(), null);
        }
    }

    // ------------------------------------------------------------------ вспомогательное

    private record Parsed(String title, String country, Integer year, User owner, List<Sku> skus, List<Tag> tags) {
    }

    private Parsed parse(CaseForm form, Map<String, String> errors) {
        String title = blank(form.getTitle());
        if (title == null) {
            errors.put("title", "validation.required");
        }
        String country = blank(form.getCountry());
        if (country == null) {
            errors.put("country", "validation.required");
        }
        Integer year = null;
        if (blank(form.getYear()) != null) {
            try {
                year = Integer.valueOf(form.getYear().trim());
                if (year < 1990 || year > 2100) {
                    errors.put("year", "validation.year.invalid");
                }
            } catch (NumberFormatException e) {
                errors.put("year", "validation.year.invalid");
            }
        }
        User owner = form.getOwnerId() == null ? null : users.findById(form.getOwnerId()).orElse(null);
        if (owner == null) {
            errors.put("ownerId", "validation.required");
        }
        List<Sku> skus = skuService.resolveModels(form.getSkus(), errors, "skus");
        if (skus.isEmpty() && !errors.containsKey("skus")) {
            errors.put("skus", "validation.case.skuRequired");
        }
        List<Tag> tagList = form.getTagIds() == null || form.getTagIds().isEmpty() ? List.of()
                : tags.findAllById(form.getTagIds());
        return new Parsed(title, country, year, owner, skus, tagList);
    }

    private void apply(CaseStudy c, CaseForm form, Parsed p) {
        c.setTitle(p.title);
        c.setCountry(p.country);
        c.setCity(blank(form.getCity()));
        c.setCustomerName(blank(form.getCustomerName()));
        c.setCustomerVisible(form.isCustomerVisible());
        c.setSummary(blank(form.getSummary()));
        c.setChallenge(blank(form.getChallenge()));
        c.setSolution(blank(form.getSolution()));
        c.setResults(blank(form.getResults()));
        c.setDeploymentSize(blank(form.getDeploymentSize()));
        c.setYear(p.year);
        c.setOwner(p.owner);
        c.setPublicationAllowed(form.isPublicationAllowed());
    }

    private void change(CaseStudy c, String field, Object oldValue, Object newValue, String login) {
        if (!Objects.equals(norm(oldValue), norm(newValue))) {
            audit.record(ENTITY, c.getId(), AuditAction.UPDATE, field, oldValue, newValue, login, null);
        }
    }

    private static Object norm(Object o) {
        return o instanceof String s && s.isBlank() ? null : o;
    }

    private CaseStudy load(Long id) {
        return repository.findActiveById(id).orElseThrow(() -> new CaseNotFoundException(id));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
