package ru.eltex.producthub.firmware.domain;

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
import jakarta.persistence.Table;

import ru.eltex.producthub.sku.domain.Sku;

/** Прошивка (SPEC.md 4.5): версия, статус, файл или ссылка, совместимые SKU, release notes. */
@Entity
@EntityListeners(ru.eltex.producthub.common.IndexedChangeListener.class)
@Table(name = "firmware")
public class Firmware implements ru.eltex.producthub.common.Indexable {

    @Override
    public String indexType() {
        return "FIRMWARE";
    }

    @Override
    public Long indexId() {
        return id;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String version;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FirmwareStatus status;

    @Column(name = "release_notes", length = 20000)
    private String releaseNotes;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "stored_path")
    private String storedPath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private String sha256;

    /** Можно ли показывать прошивку во внешнем контуре. */
    @Column(name = "is_public", nullable = false)
    private boolean publicRelease;

    @Column(name = "published_by", nullable = false)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "firmware_sku", joinColumns = @JoinColumn(name = "firmware_id"),
            inverseJoinColumns = @JoinColumn(name = "sku_id"))
    private Set<Sku> skus = new LinkedHashSet<>();

    protected Firmware() {
    }

    public Firmware(String version, LocalDate releaseDate, FirmwareStatus status, String publishedBy) {
        this.version = version;
        this.releaseDate = releaseDate;
        this.status = status;
        this.publishedBy = publishedBy;
        this.publishedAt = LocalDateTime.now();
        this.createdAt = this.publishedAt;
        this.updatedAt = this.publishedAt;
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public FirmwareStatus getStatus() {
        return status;
    }

    public void setStatus(FirmwareStatus status) {
        this.status = status;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean hasFile() {
        return storedPath != null;
    }

    public void setFile(String fileName, String storedPath, long sizeBytes, String sha256) {
        this.fileName = fileName;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public boolean isPublicRelease() {
        return publicRelease;
    }

    public void setPublicRelease(boolean publicRelease) {
        this.publicRelease = publicRelease;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<Sku> getSkus() {
        return skus;
    }
}
