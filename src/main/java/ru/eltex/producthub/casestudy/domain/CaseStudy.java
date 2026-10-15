package ru.eltex.producthub.casestudy.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.eltex.producthub.artifact.domain.Artifact;
import ru.eltex.producthub.security.User;
import ru.eltex.producthub.sku.domain.Sku;
import ru.eltex.producthub.tag.domain.Tag;

/** Кейс (референс внедрения), SPEC.md 4.6. Отрасль задается тегами, а не отдельным полем. */
@Entity
@EntityListeners(ru.eltex.producthub.common.IndexedChangeListener.class)
@Table(name = "case_study")
public class CaseStudy implements ru.eltex.producthub.common.Indexable {

    @Override
    public String indexType() {
        return "CASE";
    }

    @Override
    public Long indexId() {
        return id;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "case_sku", joinColumns = @JoinColumn(name = "case_id"), inverseJoinColumns = @JoinColumn(name = "sku_id"))
    private Set<Sku> skus = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "case_tag", joinColumns = @JoinColumn(name = "case_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    /** Вложения (PDF кейса, фото) хранятся как артефакты типа "Кейс / референс внедрения". */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "case_artifact", joinColumns = @JoinColumn(name = "case_id"), inverseJoinColumns = @JoinColumn(name = "artifact_id"))
    private Set<Artifact> artifacts = new LinkedHashSet<>();

    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "country", nullable = false)
    private String country;
    @Column(name = "city")
    private String city;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "customer_visible", nullable = false)
    private boolean customerVisible;
    @Column(name = "summary")
    private String summary;
    @Column(name = "challenge")
    private String challenge;
    @Column(name = "solution")
    private String solution;
    @Column(name = "results")
    private String results;
    @Column(name = "deployment_size")
    private String deploymentSize;
    @Column(name = "case_year")
    private Integer year;
    @Column(name = "publication_allowed", nullable = false)
    private boolean publicationAllowed;
    @Column(name = "deleted", nullable = false)
    private boolean deleted;
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    protected CaseStudy() {
    }

    public CaseStudy(String title, String country, User owner, String createdBy) {
        this.title = title;
        this.country = country;
        this.owner = owner;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Set<Sku> getSkus() {
        return skus;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public Set<Artifact> getArtifacts() {
        return artifacts;
    }
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
    public boolean isCustomerVisible() {
        return customerVisible;
    }

    public void setCustomerVisible(boolean customerVisible) {
        this.customerVisible = customerVisible;
    }
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }
    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }
    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }
    public String getDeploymentSize() {
        return deploymentSize;
    }

    public void setDeploymentSize(String deploymentSize) {
        this.deploymentSize = deploymentSize;
    }
    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
    public boolean isPublicationAllowed() {
        return publicationAllowed;
    }

    public void setPublicationAllowed(boolean publicationAllowed) {
        this.publicationAllowed = publicationAllowed;
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
}
