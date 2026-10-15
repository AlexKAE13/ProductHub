package ru.eltex.producthub.sku.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.eltex.producthub.security.User;
import ru.eltex.producthub.tag.domain.Tag;

/** SKU (артикул), SPEC.md 4.2. Обязательные поля отмечены в миграции V2. */
@Entity
@EntityListeners(ru.eltex.producthub.common.IndexedChangeListener.class)
@Table(name = "sku")
public class Sku implements ru.eltex.producthub.common.Indexable {

    @Override
    public String indexType() {
        return "SKU";
    }

    @Override
    public Long indexId() {
        return id;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id")
    private ProductLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successor_id")
    private Sku successor;

    /** Все владельцы SKU, включая основного (поле owner). Основной получает автоматические задачи и вопросы. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sku_owner", joinColumns = @JoinColumn(name = "sku_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @org.hibernate.annotations.BatchSize(size = 100)
    private Set<User> owners = new LinkedHashSet<>();

    /** Теги SKU: рынки, сегменты, темы (любые теги из справочника). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sku_tag", joinColumns = @JoinColumn(name = "sku_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "model", nullable = false)
    private String model;
    @Column(name = "model_normalized", nullable = false)
    private String modelNormalized;
    @Column(name = "name_ru")
    private String nameRu;
    @Column(name = "name_en")
    private String nameEn;
    @Column(name = "category", nullable = false)
    private String category;
    @Column(name = "site_section_ru")
    private String siteSectionRu;
    @Column(name = "site_subsection_ru")
    private String siteSubsectionRu;
    @Column(name = "site_section_en")
    private String siteSectionEn;
    @Column(name = "site_subsection_en")
    private String siteSubsectionEn;
    @Column(name = "site_status_ru")
    private String siteStatusRu;
    @Column(name = "site_status_en")
    private String siteStatusEn;
    @Column(name = "chip")
    private String chip;
    @Column(name = "ga_date")
    private String gaDate;
    @Column(name = "eol_announce_date")
    private String eolAnnounceDate;
    @Column(name = "last_order_date")
    private String lastOrderDate;
    @Column(name = "last_ship_date")
    private String lastShipDate;
    @Column(name = "end_of_support_date")
    private String endOfSupportDate;
    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;
    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;
    @Column(name = "price")
    private BigDecimal price;
    @Column(name = "currency")
    private String currency;
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;
    @Column(name = "commercial_updated_at")
    private LocalDateTime commercialUpdatedAt;
    @Column(name = "commercial_updated_by")
    private String commercialUpdatedBy;
    @Column(name = "description")
    private String description;
    @Column(name = "key_features")
    private String keyFeatures;
    @Column(name = "plans")
    private String plans;
    @Column(name = "completeness", nullable = false)
    private int completeness;
    @Column(name = "deleted", nullable = false)
    private boolean deleted;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private LifecycleStatus lifecycleStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "price_list")
    private PriceList priceList;
    @Enumerated(EnumType.STRING)
    @Column(name = "availability")
    private Availability availability;
    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_source")
    private CommercialSource commercialSource;
    @Enumerated(EnumType.STRING)
    @Column(name = "contour", nullable = false)
    private Contour contour;
    protected Sku() {
    }

    public Sku(String model, String modelNormalized, ProductLine line, String category, User owner,
               LifecycleStatus lifecycleStatus) {
        this.model = model;
        this.modelNormalized = modelNormalized;
        this.line = line;
        this.category = category;
        this.owner = owner;
        this.owners.add(owner);
        this.lifecycleStatus = lifecycleStatus;
        this.contour = Contour.INTERNAL;
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Set<User> getOwners() {
        return owners;
    }

    /** Имена всех владельцев через запятую, основной первым. */
    public String getOwnerNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add(owner.getFullName());
        owners.stream().filter(u -> !u.getId().equals(owner.getId())).map(User::getFullName).sorted().forEach(names::add);
        return String.join(", ", names);
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId) || owners.stream().anyMatch(u -> u.getId().equals(userId));
    }

    public Set<Tag> getTags() {
        return tags;
    }
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
    public String getModelNormalized() {
        return modelNormalized;
    }

    public void setModelNormalized(String modelNormalized) {
        this.modelNormalized = modelNormalized;
    }
    public String getNameRu() {
        return nameRu;
    }

    public void setNameRu(String nameRu) {
        this.nameRu = nameRu;
    }
    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
    public String getSiteSectionRu() {
        return siteSectionRu;
    }

    public void setSiteSectionRu(String siteSectionRu) {
        this.siteSectionRu = siteSectionRu;
    }
    public String getSiteSubsectionRu() {
        return siteSubsectionRu;
    }

    public void setSiteSubsectionRu(String siteSubsectionRu) {
        this.siteSubsectionRu = siteSubsectionRu;
    }
    public String getSiteSectionEn() {
        return siteSectionEn;
    }

    public void setSiteSectionEn(String siteSectionEn) {
        this.siteSectionEn = siteSectionEn;
    }
    public String getSiteSubsectionEn() {
        return siteSubsectionEn;
    }

    public void setSiteSubsectionEn(String siteSubsectionEn) {
        this.siteSubsectionEn = siteSubsectionEn;
    }
    public String getSiteStatusRu() {
        return siteStatusRu;
    }

    public void setSiteStatusRu(String siteStatusRu) {
        this.siteStatusRu = siteStatusRu;
    }
    public String getSiteStatusEn() {
        return siteStatusEn;
    }

    public void setSiteStatusEn(String siteStatusEn) {
        this.siteStatusEn = siteStatusEn;
    }
    public String getChip() {
        return chip;
    }

    public void setChip(String chip) {
        this.chip = chip;
    }
    public String getGaDate() {
        return gaDate;
    }

    public void setGaDate(String gaDate) {
        this.gaDate = gaDate;
    }
    public String getEolAnnounceDate() {
        return eolAnnounceDate;
    }

    public void setEolAnnounceDate(String eolAnnounceDate) {
        this.eolAnnounceDate = eolAnnounceDate;
    }
    public String getLastOrderDate() {
        return lastOrderDate;
    }

    public void setLastOrderDate(String lastOrderDate) {
        this.lastOrderDate = lastOrderDate;
    }
    public String getLastShipDate() {
        return lastShipDate;
    }

    public void setLastShipDate(String lastShipDate) {
        this.lastShipDate = lastShipDate;
    }
    public String getEndOfSupportDate() {
        return endOfSupportDate;
    }

    public void setEndOfSupportDate(String endOfSupportDate) {
        this.endOfSupportDate = endOfSupportDate;
    }
    public LocalDate getLastReviewDate() {
        return lastReviewDate;
    }

    public void setLastReviewDate(LocalDate lastReviewDate) {
        this.lastReviewDate = lastReviewDate;
    }
    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }
    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(Integer leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }
    public LocalDateTime getCommercialUpdatedAt() {
        return commercialUpdatedAt;
    }

    public void setCommercialUpdatedAt(LocalDateTime commercialUpdatedAt) {
        this.commercialUpdatedAt = commercialUpdatedAt;
    }
    public String getCommercialUpdatedBy() {
        return commercialUpdatedBy;
    }

    public void setCommercialUpdatedBy(String commercialUpdatedBy) {
        this.commercialUpdatedBy = commercialUpdatedBy;
    }
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    public String getKeyFeatures() {
        return keyFeatures;
    }

    public void setKeyFeatures(String keyFeatures) {
        this.keyFeatures = keyFeatures;
    }
    public String getPlans() {
        return plans;
    }

    public void setPlans(String plans) {
        this.plans = plans;
    }
    public int getCompleteness() {
        return completeness;
    }

    public void setCompleteness(int completeness) {
        this.completeness = completeness;
    }
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public LifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(LifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }
    public PriceList getPriceList() {
        return priceList;
    }

    public void setPriceList(PriceList priceList) {
        this.priceList = priceList;
    }
    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }
    public CommercialSource getCommercialSource() {
        return commercialSource;
    }

    public void setCommercialSource(CommercialSource commercialSource) {
        this.commercialSource = commercialSource;
    }
    public Contour getContour() {
        return contour;
    }

    public void setContour(Contour contour) {
        this.contour = contour;
    }
    public ProductLine getLine() {
        return line;
    }

    public void setLine(ProductLine line) {
        this.line = line;
    }
    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
        this.owners.add(owner);
    }
    public Sku getSuccessor() {
        return successor;
    }

    public void setSuccessor(Sku successor) {
        this.successor = successor;
    }
    /** Ключевые особенности как список (в базе хранятся одной строкой, по одной на строку). */
    public java.util.List<String> getKeyFeatureList() {
        if (keyFeatures == null) {
            return java.util.List.of();
        }
        return keyFeatures.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
    }

    /** Дата истекла, если ревью просрочено на сегодня. */
    public boolean isReviewDue(LocalDate today) {
        return nextReviewDate != null && nextReviewDate.isBefore(today);
    }
}
