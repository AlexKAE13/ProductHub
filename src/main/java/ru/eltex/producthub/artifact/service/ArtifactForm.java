package ru.eltex.producthub.artifact.service;

import java.util.ArrayList;
import java.util.List;

/** Данные формы правки артефакта. SKU вводятся артикулами через запятую или перевод строки. */
public class ArtifactForm {

    private String title;
    private String description;
    private Long typeId;
    private String language;
    private String status;
    private Long ownerId;
    private String contour;
    private String externalUrl;
    private Long lineId;
    private String skus;
    private List<Long> tagIds = new ArrayList<>();
    private String reviewPeriodDays;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getContour() {
        return contour;
    }

    public void setContour(String contour) {
        this.contour = contour;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public Long getLineId() {
        return lineId;
    }

    public void setLineId(Long lineId) {
        this.lineId = lineId;
    }

    public String getSkus() {
        return skus;
    }

    public void setSkus(String skus) {
        this.skus = skus;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public String getReviewPeriodDays() {
        return reviewPeriodDays;
    }

    public void setReviewPeriodDays(String reviewPeriodDays) {
        this.reviewPeriodDays = reviewPeriodDays;
    }
}
