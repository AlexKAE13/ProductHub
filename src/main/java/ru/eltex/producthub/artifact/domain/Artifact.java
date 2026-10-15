package ru.eltex.producthub.artifact.domain;

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
import ru.eltex.producthub.sku.domain.Contour;
import ru.eltex.producthub.sku.domain.ProductLine;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.tag.domain.Tag;

/** Артефакт (материал) из единой базы отдела, SPEC.md 4.4. */
@Entity
@EntityListeners(ru.eltex.producthub.common.IndexedChangeListener.class)
@Table(name = "artifact")
public class Artifact implements ru.eltex.producthub.common.Indexable {

    @Override
    public String indexType() {
        return "ARTIFACT";
    }

    @Override
    public Long indexId() {
        return id;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id")
    private ArtifactType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Привязка к линейке целиком ("для всей линейки"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id")
    private ProductLine line;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private ArtifactVersion currentVersion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "artifact_sku", joinColumns = @JoinColumn(name = "artifact_id"),
            inverseJoinColumns = @JoinColumn(name = "sku_id"))
    private Set<Sku> skus = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "artifact_tag", joinColumns = @JoinColumn(name = "artifact_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "description")
    private String description;
    @Column(name = "external_url")
    private String externalUrl;
    @Column(name = "last_reviewed_at", nullable = false)
    private LocalDateTime lastReviewedAt;
    @Column(name = "review_period_days", nullable = false)
    private int reviewPeriodDays;
    @Column(name = "review_due_at", nullable = false)
    private LocalDateTime reviewDueAt;
    @Column(name = "source_key")
    private String sourceKey;
    @Column(name = "deleted", nullable = false)
    private boolean deleted;
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ArtifactStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "contour", nullable = false)
    private Contour contour;
    protected Artifact() {
    }

    public Artifact(String title, ArtifactType type, Language language, ArtifactStatus status, User owner,
                    Contour contour, String createdBy) {
        this.title = title;
        this.type = type;
        this.language = language;
        this.status = status;
        this.owner = owner;
        this.contour = contour;
        this.createdBy = createdBy;
        this.reviewPeriodDays = type.getReviewPeriodDays();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.lastReviewedAt = this.createdAt;
        this.reviewDueAt = this.lastReviewedAt.plusDays(reviewPeriodDays);
        this.deleted = false;
    }

    public Long getId() {
        return id;
    }

    /** Подтверждение актуальности: сдвигает срок следующего ревью. */
    public void markReviewed(LocalDateTime when) {
        this.lastReviewedAt = when;
        this.reviewDueAt = when.plusDays(reviewPeriodDays);
        this.updatedAt = when;
    }

    public void changeReviewPeriod(int days) {
        this.reviewPeriodDays = days;
        this.reviewDueAt = lastReviewedAt.plusDays(days);
    }
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
    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }
    public LocalDateTime getLastReviewedAt() {
        return lastReviewedAt;
    }
    public int getReviewPeriodDays() {
        return reviewPeriodDays;
    }
    public LocalDateTime getReviewDueAt() {
        return reviewDueAt;
    }
    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }
    public ArtifactStatus getStatus() {
        return status;
    }

    public void setStatus(ArtifactStatus status) {
        this.status = status;
    }
    public Contour getContour() {
        return contour;
    }

    public void setContour(Contour contour) {
        this.contour = contour;
    }
    public ArtifactType getType() {
        return type;
    }

    public void setType(ArtifactType type) {
        this.type = type;
    }
    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }
    public ProductLine getLine() {
        return line;
    }

    public void setLine(ProductLine line) {
        this.line = line;
    }
    public ArtifactVersion getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(ArtifactVersion currentVersion) {
        this.currentVersion = currentVersion;
    }
    public Set<Sku> getSkus() {
        return skus;
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
