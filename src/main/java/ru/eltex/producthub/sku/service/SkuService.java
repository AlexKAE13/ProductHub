package ru.eltex.producthub.sku.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.eltex.producthub.common.AuditAction;
import ru.eltex.producthub.common.AuditService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.security.UserRepository;
import ru.eltex.producthub.sku.domain.Availability;
import ru.eltex.producthub.sku.domain.CommercialSource;
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.LifecycleStatus;
import ru.eltex.producthub.sku.domain.PriceList;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecDefinition;
import ru.eltex.producthub.sku.domain.SpecValue;
import ru.eltex.producthub.sku.domain.SpecValueType;
import ru.eltex.producthub.sku.repo.ProductLineRepository;
import ru.eltex.producthub.sku.repo.SkuRepository;
import ru.eltex.producthub.sku.repo.SpecValueRepository;
import ru.eltex.producthub.tag.domain.Tag;
import ru.eltex.producthub.tag.repo.TagRepository;

/** Бизнес-логика SKU: карточка, создание, копирование, правка по блокам, ревью, мягкое удаление. */
@Service
public class SkuService {

    public static final String ENTITY = "SKU";
    static final int HISTORY_LIMIT = 50;

    private final SkuRepository skus;
    private final SpecValueRepository specValues;
    private final ProductLineRepository lines;
    private final UserRepository users;
    private final ReferenceDataService reference;
    private final SkuPermissions permissions;
    private final CompletenessCalculator completeness;
    private final AuditService audit;
    private final int reviewPeriodDays;
    private final org.springframework.beans.factory.ObjectProvider<SkuMaterials> materials;
    private final TagRepository tagRepository;
    private final org.springframework.context.ApplicationEventPublisher events;

    public SkuService(SkuRepository skus, SpecValueRepository specValues, ProductLineRepository lines,
                      UserRepository users, ReferenceDataService reference, SkuPermissions permissions,
                      CompletenessCalculator completeness, AuditService audit,
                      @org.springframework.beans.factory.annotation.Value("${producthub.review-period-days:90}")
                      int reviewPeriodDays,
                      org.springframework.beans.factory.ObjectProvider<SkuMaterials> materials,
                      TagRepository tagRepository,
                      org.springframework.context.ApplicationEventPublisher events) {
        this.skus = skus;
        this.specValues = specValues;
        this.lines = lines;
        this.users = users;
        this.reference = reference;
        this.permissions = permissions;
        this.completeness = completeness;
        this.audit = audit;
        this.reviewPeriodDays = reviewPeriodDays;
        this.materials = materials;
        this.tagRepository = tagRepository;
        this.events = events;
    }

    // ---------------------------------------------------------------- чтение

    @Transactional(readOnly = true)
    public Long findIdByModel(String model) {
        return skus.findCardByNormalized(ModelNormalizer.normalize(model)).map(Sku::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public SkuCard loadCard(String model, HubUserDetails user) {
        Sku sku = skus.findCardByNormalized(ModelNormalizer.normalize(model))
                .orElseThrow(() -> new SkuNotFoundException(model));
        return buildCard(sku, user);
    }

    @Transactional(readOnly = true)
    public SkuCard loadCard(Long id, HubUserDetails user) {
        Sku sku = skus.findCardById(id).orElseThrow(() -> new SkuNotFoundException(String.valueOf(id)));
        return buildCard(sku, user);
    }

    private SkuCard buildCard(Sku sku, HubUserDetails user) {
        if (!permissions.canView(user, sku)) {
            throw new AccessDeniedException("SKU недоступен во внешнем контуре");
        }
        Set<CardSection> visible = EnumSet.noneOf(CardSection.class);
        for (CardSection s : CardSection.values()) {
            if (permissions.canSee(s, user, sku)) {
                visible.add(s);
            }
        }
        List<SpecValue> values = specValues.findBySkuId(sku.getId());
        int[] empty = new int[1];
        List<SkuCard.SpecGroup> groups = visible.contains(CardSection.SPECS)
                ? specGroups(sku, values, user, empty) : List.of();
        List<Sku> predecessors = visible.contains(CardSection.LIFECYCLE)
                ? skus.findPredecessors(sku.getId()) : List.of();
        var history = visible.contains(CardSection.HISTORY)
                ? audit.history(ENTITY, sku.getId(), HISTORY_LIMIT) : List.<ru.eltex.producthub.common.AuditLog>of();
        boolean stale = completeness.isCommercialStale(sku, LocalDate.now());
        return new SkuCard(sku, groups, predecessors, history, visible, permissions.canEditSku(user),
                permissions.canEditAnySpec(user), stale, empty[0], skus.findTags(sku.getId()));
    }

    /** Показываются только заполненные характеристики. Пустые, которые пользователь может заполнить, считаются отдельно. */
    private List<SkuCard.SpecGroup> specGroups(Sku sku, List<SpecValue> values, HubUserDetails user, int[] emptyCount) {
        Map<Long, String> byDef = new LinkedHashMap<>();
        values.forEach(v -> byDef.put(v.getDefinition().getId(), v.getValue()));
        Map<String, List<SkuCard.SpecRow>> groups = new LinkedHashMap<>();
        for (SpecDefinition d : reference.specDefinitionsFor(sku.getCategory())) {
            String value = byDef.get(d.getId());
            if (value == null || value.isBlank()) {
                if (permissions.canEditSpec(user, d)) {
                    emptyCount[0]++;
                }
                continue;
            }
            groups.computeIfAbsent(d.getGroup(), k -> new ArrayList<>())
                    .add(new SkuCard.SpecRow(d, value, permissions.canEditSpec(user, d)));
        }
        List<SkuCard.SpecGroup> result = new ArrayList<>();
        groups.forEach((name, rows) -> result.add(new SkuCard.SpecGroup(name, rows)));
        return result;
    }

    /** Данные формы для правки блока. */
    @Transactional(readOnly = true)
    public SkuForm formFor(Long id, HubUserDetails user) {
        Sku sku = skus.findCardById(id).orElseThrow(() -> new SkuNotFoundException(String.valueOf(id)));
        if (!permissions.canView(user, sku)) {
            throw new AccessDeniedException("SKU недоступен");
        }
        return SkuForm.from(sku, specValues.findBySkuId(id));
    }

    /** Определения характеристик, которые пользователь может править в этом SKU. */
    @Transactional(readOnly = true)
    public List<SpecDefinition> editableSpecs(Long id, HubUserDetails user) {
        Sku sku = skus.findCardById(id).orElseThrow(() -> new SkuNotFoundException(String.valueOf(id)));
        return reference.specDefinitionsFor(sku.getCategory()).stream()
                .filter(d -> permissions.canEditSpec(user, d)).toList();
    }

    /** Артикулы через запятую или перевод строки -> SKU. Ненайденные дают ошибку в поле errorField. */
    @Transactional(readOnly = true)
    public List<Sku> resolveModels(String text, Map<String, String> errors, String errorField) {
        List<Sku> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        boolean missing = false;
        for (String part : text.split("[,;\\n]")) {
            String model = part.trim();
            if (model.isEmpty()) {
                continue;
            }
            var found = skus.findByModelNormalized(ModelNormalizer.normalize(model)).filter(x -> !x.isDeleted());
            if (found.isEmpty()) {
                missing = true;
            } else if (!result.contains(found.get())) {
                result.add(found.get());
            }
        }
        if (missing) {
            errors.put(errorField, "validation.sku.notFound");
        }
        return result;
    }

    // ---------------------------------------------------------------- создание

    @Transactional
    public Sku create(SkuForm form, HubUserDetails user) {
        if (!permissions.canCreate(user)) {
            throw new AccessDeniedException("Нет прав на создание SKU");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String model = trim(form.getModel());
        if (model == null) {
            errors.put("model", "validation.required");
        } else if (skus.findByModelNormalized(ModelNormalizer.normalize(model)).isPresent()) {
            errors.put("model", "validation.model.exists");
        }
        ProductLine line = form.getLineId() == null ? null : lines.findById(form.getLineId()).orElse(null);
        if (line == null) {
            errors.put("lineId", "validation.required");
        }
        String category = trim(form.getCategory());
        if (category == null) {
            errors.put("category", "validation.required");
        }
        LifecycleStatus status = parseEnum(LifecycleStatus.class, form.getLifecycleStatus());
        if (status == null) {
            errors.put("lifecycleStatus", "validation.required");
        }
        User owner = form.getOwnerId() == null ? null : users.findById(form.getOwnerId()).orElse(null);
        if (owner == null) {
            errors.put("ownerId", "validation.required");
        }
        validateLifecycleDates(form, errors);
        if (!errors.isEmpty()) {
            throw new SkuValidationException(errors);
        }

        Sku sku = new Sku(model, ModelNormalizer.normalize(model), line, category, owner, status);
        if (form.getOwnerIds() != null && !form.getOwnerIds().isEmpty()) {
            sku.getOwners().addAll(users.findAllById(form.getOwnerIds()));
        }
        applyIdentity(sku, form, null, null);
        applyLifecycleDates(sku, form, null, null);
        sku.setContour(parseEnum(Contour.class, form.getContour()) == null
                ? Contour.INTERNAL : parseEnum(Contour.class, form.getContour()));

        Sku source = null;
        if (form.getCopyFromId() != null) {
            source = skus.findById(form.getCopyFromId()).orElse(null);
        }
        if (source != null) {
            sku.setDescription(source.getDescription());
            sku.setKeyFeatures(source.getKeyFeatures());
            sku.getTags().addAll(skus.findTags(source.getId()));
        } else {
            if (form.getTagIds() != null && !form.getTagIds().isEmpty()) {
                sku.getTags().addAll(tagRepository.findAllById(form.getTagIds()));
            }
            sku.setDescription(trim(form.getDescription()));
            sku.setKeyFeatures(trim(form.getKeyFeatures()));
        }
        skus.save(sku);

        List<SpecDefinition> template = reference.specDefinitionsFor(category);
        List<SpecValue> created = new ArrayList<>();
        if (source != null) {
            for (SpecValue v : specValues.findBySkuId(source.getId())) {
                if (template.stream().anyMatch(d -> d.getId().equals(v.getDefinition().getId()))) {
                    created.add(specValues.save(new SpecValue(sku, v.getDefinition(), v.getValue())));
                }
            }
        }
        sku.setCompleteness(completenessFor(sku, created));
        audit.record(ENTITY, sku.getId(), AuditAction.CREATE, null, null, sku.getModel(), user.getUsername(),
                source == null ? null : "копия " + source.getModel());
        reference.evictAll();
        return sku;
    }

    // ---------------------------------------------------------------- правка

    @Transactional
    public void update(Long id, SkuBlock block, SkuForm form, HubUserDetails user) {
        Sku sku = skus.findCardById(id).orElseThrow(() -> new SkuNotFoundException(String.valueOf(id)));
        if (block == SkuBlock.SPECS) {
            if (!permissions.canEditAnySpec(user)) {
                throw new AccessDeniedException("Нет прав на правку характеристик");
            }
        } else if (!permissions.canEditSku(user)) {
            throw new AccessDeniedException("Нет прав на правку SKU");
        }
        String note = permissions.canEditSku(user) && permissions.isForeign(user, sku) ? "чужой SKU" : null;
        Map<String, String> errors = new LinkedHashMap<>();
        Changes changes = new Changes();

        switch (block) {
            case MAIN -> updateMain(sku, form, errors, changes);
            case TEXT -> {
                changes.set("description", sku.getDescription(), trim(form.getDescription()), sku::setDescription);
                changes.set("keyFeatures", sku.getKeyFeatures(), trim(form.getKeyFeatures()), sku::setKeyFeatures);
            }
            case PLANS -> changes.set("plans", sku.getPlans(), trim(form.getPlans()), sku::setPlans);
            case LIFECYCLE -> updateLifecycle(sku, form, errors, changes);
            case COMMERCIAL -> updateCommercial(sku, form, user, errors, changes);
            case SPECS -> updateSpecs(sku, form, user, errors, changes);
        }
        if (!errors.isEmpty()) {
            throw new SkuValidationException(errors);
        }
        changes.apply();
        sku.setUpdatedAt(LocalDateTime.now());
        recalculate(sku);
        for (Change c : changes.list) {
            AuditAction action = c.field.equals("lifecycleStatus") ? AuditAction.STATUS_CHANGE : AuditAction.UPDATE;
            audit.record(ENTITY, sku.getId(), action, c.field, c.oldValue, c.newValue, user.getUsername(), note);
        }
        for (Change c : changes.list) {
            if (c.field.equals("lifecycleStatus") && c.oldValue instanceof LifecycleStatus from
                    && c.newValue instanceof LifecycleStatus to) {
                events.publishEvent(new ru.eltex.producthub.sku.event.SkuLifecycleChanged(sku.getId(), from, to,
                        user.getUsername()));
            }
        }
        if (changes.list.stream().anyMatch(c -> c.field.equals("category") || c.field.equals("line"))) {
            reference.evictAll();
        }
    }

    private void updateMain(Sku sku, SkuForm form, Map<String, String> errors, Changes changes) {
        String model = trim(form.getModel());
        if (model == null) {
            errors.put("model", "validation.required");
        } else if (!model.equals(sku.getModel())) {
            var existing = skus.findByModelNormalized(ModelNormalizer.normalize(model));
            if (existing.isPresent() && !existing.get().getId().equals(sku.getId())) {
                errors.put("model", "validation.model.exists");
            }
        }
        ProductLine line = form.getLineId() == null ? null : lines.findById(form.getLineId()).orElse(null);
        if (line == null) {
            errors.put("lineId", "validation.required");
        }
        String category = trim(form.getCategory());
        if (category == null) {
            errors.put("category", "validation.required");
        }
        User owner = form.getOwnerId() == null ? null : users.findById(form.getOwnerId()).orElse(null);
        if (owner == null) {
            errors.put("ownerId", "validation.required");
        }
        if (!errors.isEmpty()) {
            return;
        }
        changes.set("model", sku.getModel(), model, v -> {
            sku.setModel(v);
            sku.setModelNormalized(ModelNormalizer.normalize(v));
        });
        changes.set("line", sku.getLine().getCode(), line.getCode(), v -> sku.setLine(line));
        changes.set("category", sku.getCategory(), category, sku::setCategory);
        changes.set("owner", sku.getOwner().getLogin(), owner.getLogin(), v -> sku.setOwner(owner));
        applyOwners(sku, owner, form, changes);
        applyIdentity(sku, form, changes, errors);
        Contour contour = parseEnum(Contour.class, form.getContour());
        if (contour != null) {
            changes.set("contour", sku.getContour(), contour, sku::setContour);
        }
        List<Tag> newTags = form.getTagIds() == null || form.getTagIds().isEmpty() ? List.of()
                : tagRepository.findAllById(form.getTagIds());
        Set<Long> oldIds = sku.getTags().stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> newIds = newTags.stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
        if (!oldIds.equals(newIds)) {
            String oldText = sku.getTags().stream().map(Tag::getValue).sorted().collect(java.util.stream.Collectors.joining(", "));
            String newText = newTags.stream().map(Tag::getValue).sorted().collect(java.util.stream.Collectors.joining(", "));
            changes.set("tags", oldText, newText, v -> {
                sku.getTags().clear();
                sku.getTags().addAll(newTags);
            });
        }
    }

    /** Совладельцы: выбранные в форме плюс основной владелец (он всегда среди владельцев). */
    private void applyOwners(Sku sku, User primary, SkuForm form, Changes changes) {
        Set<User> wanted = new java.util.LinkedHashSet<>();
        wanted.add(primary);
        if (form.getOwnerIds() != null && !form.getOwnerIds().isEmpty()) {
            wanted.addAll(users.findAllById(form.getOwnerIds()));
        }
        Set<Long> oldIds = sku.getOwners().stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> newIds = wanted.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        if (!oldIds.equals(newIds)) {
            String oldText = sku.getOwners().stream().map(User::getLogin).sorted().collect(java.util.stream.Collectors.joining(", "));
            String newText = wanted.stream().map(User::getLogin).sorted().collect(java.util.stream.Collectors.joining(", "));
            changes.set("owners", oldText, newText, v -> {
                sku.getOwners().clear();
                sku.getOwners().addAll(wanted);
            });
        }
    }

    /** Общие поля идентификации (без артикула, линейки, категории и владельца). */
    private void applyIdentity(Sku sku, SkuForm form, Changes changes, Map<String, String> errors) {
        Changes target = changes == null ? new Changes() : changes;
        target.set("nameRu", sku.getNameRu(), trim(form.getNameRu()), sku::setNameRu);
        target.set("nameEn", sku.getNameEn(), trim(form.getNameEn()), sku::setNameEn);
        target.set("chip", sku.getChip(), trim(form.getChip()), sku::setChip);
        target.set("siteSectionRu", sku.getSiteSectionRu(), trim(form.getSiteSectionRu()), sku::setSiteSectionRu);
        target.set("siteSubsectionRu", sku.getSiteSubsectionRu(), trim(form.getSiteSubsectionRu()),
                sku::setSiteSubsectionRu);
        target.set("siteSectionEn", sku.getSiteSectionEn(), trim(form.getSiteSectionEn()), sku::setSiteSectionEn);
        target.set("siteSubsectionEn", sku.getSiteSubsectionEn(), trim(form.getSiteSubsectionEn()),
                sku::setSiteSubsectionEn);
        target.set("siteStatusRu", sku.getSiteStatusRu(), trim(form.getSiteStatusRu()), sku::setSiteStatusRu);
        target.set("siteStatusEn", sku.getSiteStatusEn(), trim(form.getSiteStatusEn()), sku::setSiteStatusEn);
        if (changes == null) {
            target.apply();
        }
    }

    private void updateLifecycle(Sku sku, SkuForm form, Map<String, String> errors, Changes changes) {
        LifecycleStatus status = parseEnum(LifecycleStatus.class, form.getLifecycleStatus());
        if (status == null) {
            errors.put("lifecycleStatus", "validation.required");
        }
        validateLifecycleDates(form, errors);
        Sku successor = null;
        String successorModel = trim(form.getSuccessorModel());
        if (successorModel != null) {
            successor = skus.findByModelNormalized(ModelNormalizer.normalize(successorModel)).orElse(null);
            if (successor == null || successor.isDeleted()) {
                errors.put("successorModel", "validation.successor.notFound");
            } else if (successor.getId().equals(sku.getId())) {
                errors.put("successorModel", "validation.successor.self");
            }
        }
        if (!errors.isEmpty()) {
            return;
        }
        changes.set("lifecycleStatus", sku.getLifecycleStatus(), status, sku::setLifecycleStatus);
        applyLifecycleDates(sku, form, changes, errors);
        Sku finalSuccessor = successor;
        changes.set("successor", sku.getSuccessor() == null ? null : sku.getSuccessor().getModel(),
                successor == null ? null : successor.getModel(), v -> sku.setSuccessor(finalSuccessor));
        changes.set("lastReviewDate", sku.getLastReviewDate(), parseDate(form.getLastReviewDate()),
                sku::setLastReviewDate);
        changes.set("nextReviewDate", sku.getNextReviewDate(), parseDate(form.getNextReviewDate()),
                sku::setNextReviewDate);
    }

    private void applyLifecycleDates(Sku sku, SkuForm form, Changes changes, Map<String, String> errors) {
        Changes target = changes == null ? new Changes() : changes;
        target.set("gaDate", sku.getGaDate(), LifecycleDate.normalize(form.getGaDate()), sku::setGaDate);
        target.set("eolAnnounceDate", sku.getEolAnnounceDate(), LifecycleDate.normalize(form.getEolAnnounceDate()),
                sku::setEolAnnounceDate);
        target.set("lastOrderDate", sku.getLastOrderDate(), LifecycleDate.normalize(form.getLastOrderDate()),
                sku::setLastOrderDate);
        target.set("lastShipDate", sku.getLastShipDate(), LifecycleDate.normalize(form.getLastShipDate()),
                sku::setLastShipDate);
        target.set("endOfSupportDate", sku.getEndOfSupportDate(), LifecycleDate.normalize(form.getEndOfSupportDate()),
                sku::setEndOfSupportDate);
        if (changes == null) {
            target.apply();
        }
    }

    private void validateLifecycleDates(SkuForm form, Map<String, String> errors) {
        checkDate("gaDate", form.getGaDate(), errors);
        checkDate("eolAnnounceDate", form.getEolAnnounceDate(), errors);
        checkDate("lastOrderDate", form.getLastOrderDate(), errors);
        checkDate("lastShipDate", form.getLastShipDate(), errors);
        checkDate("endOfSupportDate", form.getEndOfSupportDate(), errors);
        for (var e : Map.of("lastReviewDate", nullToEmpty(form.getLastReviewDate()),
                "nextReviewDate", nullToEmpty(form.getNextReviewDate())).entrySet()) {
            if (!e.getValue().isBlank() && parseDate(e.getValue()) == null) {
                errors.put(e.getKey(), "validation.date.invalid");
            }
        }
    }

    private void checkDate(String field, String value, Map<String, String> errors) {
        String v = LifecycleDate.normalize(value);
        if (v != null && !LifecycleDate.isValid(v)) {
            errors.put(field, "validation.lifecycleDate.invalid");
        }
    }

    private void updateCommercial(Sku sku, SkuForm form, HubUserDetails user, Map<String, String> errors,
                                  Changes changes) {
        BigDecimal price = null;
        String priceText = trim(form.getPrice());
        if (priceText != null) {
            try {
                price = new BigDecimal(priceText.replace(',', '.').replace(" ", ""));
                if (price.signum() < 0) {
                    errors.put("price", "validation.number.invalid");
                }
            } catch (NumberFormatException e) {
                errors.put("price", "validation.number.invalid");
            }
        }
        Integer lead = null;
        String leadText = trim(form.getLeadTimeDays());
        if (leadText != null) {
            try {
                lead = Integer.valueOf(leadText);
                if (lead < 0) {
                    errors.put("leadTimeDays", "validation.number.invalid");
                }
            } catch (NumberFormatException e) {
                errors.put("leadTimeDays", "validation.number.invalid");
            }
        }
        String currency = trim(form.getCurrency());
        if (currency != null && !currency.matches("[A-Za-z]{3}")) {
            errors.put("currency", "validation.currency.invalid");
        }
        if (!errors.isEmpty()) {
            return;
        }
        changes.set("price", sku.getPrice() == null ? null : sku.getPrice().stripTrailingZeros().toPlainString(),
                price == null ? null : price.stripTrailingZeros().toPlainString(),
                v -> sku.setPrice(v == null ? null : new BigDecimal(v)));
        changes.set("currency", sku.getCurrency(), currency == null ? null : currency.toUpperCase(), sku::setCurrency);
        changes.set("priceList", sku.getPriceList(), parseEnum(PriceList.class, form.getPriceList()), sku::setPriceList);
        changes.set("availability", sku.getAvailability(), parseEnum(Availability.class, form.getAvailability()),
                sku::setAvailability);
        Integer finalLead = lead;
        changes.set("leadTimeDays", sku.getLeadTimeDays(), lead, v -> sku.setLeadTimeDays(finalLead));
        // Сохранение блока подтверждает актуальность данных, даже если цифры не изменились.
        changes.always(() -> {
            sku.setCommercialUpdatedAt(LocalDateTime.now());
            sku.setCommercialUpdatedBy(user.getUsername());
            sku.setCommercialSource(CommercialSource.MANUAL);
        });
    }

    private void updateSpecs(Sku sku, SkuForm form, HubUserDetails user, Map<String, String> errors, Changes changes) {
        List<SpecDefinition> template = reference.specDefinitionsFor(sku.getCategory());
        Map<Long, SpecValue> existing = new LinkedHashMap<>();
        specValues.findBySkuId(sku.getId()).forEach(v -> existing.put(v.getDefinition().getId(), v));
        for (SpecDefinition d : template) {
            if (!permissions.canEditSpec(user, d) || !form.getSpecs().containsKey(d.getId())) {
                continue;
            }
            String value = trim(form.getSpecs().get(d.getId()));
            if (value != null && !validSpec(d, value)) {
                errors.put("specs[" + d.getId() + "]", d.getValueType() == SpecValueType.ENUM
                        ? "validation.enum.invalid" : "validation.number.invalid");
                continue;
            }
            SpecValue current = existing.get(d.getId());
            String old = current == null ? null : current.getValue();
            String field = "spec:" + d.getKey();
            if (!Objects.equals(old, value)) {
                changes.list.add(new Change(field, old, value));
                changes.actions.add(() -> {
                    if (value == null) {
                        specValues.delete(current);
                    } else if (current == null) {
                        specValues.save(new SpecValue(sku, d, value));
                    } else {
                        current.setValue(value);
                    }
                });
            }
        }
    }

    private boolean validSpec(SpecDefinition d, String value) {
        return switch (d.getValueType()) {
            case NUMBER -> value.replace(',', '.').matches("-?\\d+(\\.\\d+)?");
            case ENUM -> d.getEnumOptions().contains(value);
            case BOOLEAN, TEXT -> true;
        };
    }

    private void recalculate(Sku sku) {
        specValues.flush();
        sku.setCompleteness(completenessFor(sku, specValues.findBySkuId(sku.getId())));
    }

    /** Заполненность SKU с учетом материалов (если модуль материалов подключен). */
    public int completenessFor(Sku sku, java.util.Collection<SpecValue> values) {
        SkuMaterials provider = materials.getIfAvailable();
        MaterialsSummary summary = provider == null || sku.getId() == null ? null : provider.summary(sku.getId());
        return completeness.compute(sku, values, reference.specDefinitionsFor(sku.getCategory()), LocalDateTime.now(),
                summary);
    }

    /** Пересчет заполненности после изменения материалов. Права не проверяются: вызывают сервисы модулей. */
    @Transactional
    public void refreshCompleteness(Long skuId) {
        skus.findCardById(skuId).ifPresent(this::recalculate);
    }

    /** Кнопка "Ревью проведено". */
    @Transactional
    public void markReviewed(Long id, HubUserDetails user) {
        if (!permissions.canEditSku(user)) {
            throw new AccessDeniedException("Нет прав на ревью SKU");
        }
        Sku sku = skus.findCardById(id).orElseThrow(() -> new SkuNotFoundException(String.valueOf(id)));
        LocalDate today = LocalDate.now();
        String note = permissions.isForeign(user, sku) ? "чужой SKU" : null;
        audit.recordChange(ENTITY, id, AuditAction.UPDATE, "lastReviewDate", sku.getLastReviewDate(), today,
                user.getUsername(), note);
        audit.recordChange(ENTITY, id, AuditAction.UPDATE, "nextReviewDate", sku.getNextReviewDate(),
                today.plusDays(reviewPeriodDays), user.getUsername(), note);
        sku.setLastReviewDate(today);
        sku.setNextReviewDate(today.plusDays(reviewPeriodDays));
        sku.setUpdatedAt(LocalDateTime.now());
        recalculate(sku);
    }

    /** Мягкое удаление (флаг deleted). */
    @Transactional
    public void delete(Long id, HubUserDetails user) {
        if (!permissions.canEditSku(user)) {
            throw new AccessDeniedException("Нет прав на удаление SKU");
        }
        Sku sku = skus.findCardById(id).orElseThrow(() -> new SkuNotFoundException(String.valueOf(id)));
        sku.setDeleted(true);
        sku.setUpdatedAt(LocalDateTime.now());
        audit.record(ENTITY, id, AuditAction.DELETE, null, sku.getModel(), null, user.getUsername(),
                permissions.isForeign(user, sku) ? "чужой SKU" : null);
        reference.evictAll();
    }

    // ---------------------------------------------------------------- утилиты

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static LocalDate parseDate(String s) {
        String t = trim(s);
        if (t == null) {
            return null;
        }
        try {
            return LocalDate.parse(t);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        String t = trim(value);
        if (t == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Change(String field, Object oldValue, Object newValue) {
    }

    /** Накапливает изменения, чтобы сначала проверить всю форму, и только потом менять сущность. */
    private static final class Changes {
        final List<Change> list = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        <T> void set(String field, Object oldValue, T newValue, Consumer<T> setter) {
            if (!Objects.equals(emptyToNull(oldValue), emptyToNull(newValue))) {
                list.add(new Change(field, oldValue, newValue));
                actions.add(() -> setter.accept(newValue));
            }
        }

        void always(Runnable action) {
            actions.add(action);
        }

        void apply() {
            actions.forEach(Runnable::run);
        }

        private static Object emptyToNull(Object o) {
            return o instanceof String s && s.isBlank() ? null : o;
        }
    }
}
