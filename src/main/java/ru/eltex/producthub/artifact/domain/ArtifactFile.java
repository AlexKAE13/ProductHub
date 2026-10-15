package ru.eltex.producthub.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "artifact_file")
public class ArtifactFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private ArtifactVersion version;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(nullable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileRole role;

    protected ArtifactFile() {
    }

    public ArtifactFile(ArtifactVersion version, String fileName, String storedPath, long sizeBytes, String mimeType,
                        String sha256, FileRole role) {
        this.version = version;
        this.fileName = fileName;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
        this.sha256 = sha256;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public ArtifactVersion getVersion() {
        return version;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getSha256() {
        return sha256;
    }

    /** Можно ли показывать файл в браузере без скачивания (pdf, png, jpg). */
    public boolean isInlineSafe() {
        return ru.eltex.producthub.artifact.storage.FileTypes.isInlineSafe(fileName);
    }

    public boolean isImage() {
        return ru.eltex.producthub.artifact.storage.FileTypes.isImage(fileName);
    }

    public FileRole getRole() {
        return role;
    }
}
