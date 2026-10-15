package ru.eltex.producthub.sku.service;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.sku.domain.SpecValue;

/** Данные формы SKU. Все значения строки, разбор и проверка в {@link SkuService}. */
public class SkuForm {

    private String model;
    private String nameRu;
    private String nameEn;
    private Long lineId;
    private String category;
    private Long ownerId;
    private java.util.List<Long> ownerIds = new java.util.ArrayList<>();
    private String chip;
    private String siteSectionRu;
    private String siteSubsectionRu;
    private String siteSectionEn;
    private String siteSubsectionEn;
    private String siteStatusRu;
    private String siteStatusEn;
    private String contour;

    private String lifecycleStatus;
    private String gaDate;
    private String eolAnnounceDate;
    private String lastOrderDate;
    private String lastShipDate;
    private String endOfSupportDate;
    private String successorModel;
    private String lastReviewDate;
    private String nextReviewDate;

    private String price;
    private String currency;
    private String priceList;
    private String availability;
    private String leadTimeDays;

    private String description;
    private String keyFeatures;
    private String plans;

    private java.util.List<Long> tagIds = new java.util.ArrayList<>();
    private Map<Long, String> specs = new LinkedHashMap<>();
    private Long copyFromId;

    public static SkuForm from(Sku sku, Iterable<SpecValue> values) {
        SkuForm f = new SkuForm();
        f.model = sku.getModel();
        f.nameRu = sku.getNameRu();
        f.nameEn = sku.getNameEn();
        f.lineId = sku.getLine().getId();
        f.category = sku.getCategory();
        f.ownerId = sku.getOwner().getId();
        f.ownerIds = new java.util.ArrayList<>(sku.getOwners().stream().map(ru.eltex.producthub.security.User::getId).toList());
        f.chip = sku.getChip();
        f.siteSectionRu = sku.getSiteSectionRu();
        f.siteSubsectionRu = sku.getSiteSubsectionRu();
        f.siteSectionEn = sku.getSiteSectionEn();
        f.siteSubsectionEn = sku.getSiteSubsectionEn();
        f.siteStatusRu = sku.getSiteStatusRu();
        f.siteStatusEn = sku.getSiteStatusEn();
        f.contour = sku.getContour().name();
        f.lifecycleStatus = sku.getLifecycleStatus().name();
        f.gaDate = sku.getGaDate();
        f.eolAnnounceDate = sku.getEolAnnounceDate();
        f.lastOrderDate = sku.getLastOrderDate();
        f.lastShipDate = sku.getLastShipDate();
        f.endOfSupportDate = sku.getEndOfSupportDate();
        f.successorModel = sku.getSuccessor() == null ? null : sku.getSuccessor().getModel();
        f.lastReviewDate = sku.getLastReviewDate() == null ? null : sku.getLastReviewDate().toString();
        f.nextReviewDate = sku.getNextReviewDate() == null ? null : sku.getNextReviewDate().toString();
        f.price = sku.getPrice() == null ? null : sku.getPrice().toPlainString();
        f.currency = sku.getCurrency();
        f.priceList = sku.getPriceList() == null ? null : sku.getPriceList().name();
        f.availability = sku.getAvailability() == null ? null : sku.getAvailability().name();
        f.leadTimeDays = sku.getLeadTimeDays() == null ? null : sku.getLeadTimeDays().toString();
        f.description = sku.getDescription();
        f.keyFeatures = sku.getKeyFeatures();
        f.plans = sku.getPlans();
        sku.getTags().forEach(t -> f.tagIds.add(t.getId()));
        for (SpecValue v : values) {
            f.specs.put(v.getDefinition().getId(), v.getValue());
        }
        return f;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public Long getLineId() {
        return lineId;
    }

    public void setLineId(Long lineId) {
        this.lineId = lineId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public java.util.List<Long> getOwnerIds() {
        return ownerIds;
    }

    public void setOwnerIds(java.util.List<Long> ownerIds) {
        this.ownerIds = ownerIds == null ? new java.util.ArrayList<>() : ownerIds;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getChip() {
        return chip;
    }

    public void setChip(String chip) {
        this.chip = chip;
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

    public String getContour() {
        return contour;
    }

    public void setContour(String contour) {
        this.contour = contour;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
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

    public String getSuccessorModel() {
        return successorModel;
    }

    public void setSuccessorModel(String successorModel) {
        this.successorModel = successorModel;
    }

    public String getLastReviewDate() {
        return lastReviewDate;
    }

    public void setLastReviewDate(String lastReviewDate) {
        this.lastReviewDate = lastReviewDate;
    }

    public String getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(String nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPriceList() {
        return priceList;
    }

    public void setPriceList(String priceList) {
        this.priceList = priceList;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(String leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
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

    public Map<Long, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<Long, String> specs) {
        this.specs = specs;
    }

    public Long getCopyFromId() {
        return copyFromId;
    }

    public void setCopyFromId(Long copyFromId) {
        this.copyFromId = copyFromId;
    }

    public java.util.List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(java.util.List<Long> tagIds) {
        this.tagIds = tagIds == null ? new java.util.ArrayList<>() : tagIds;
    }
}
