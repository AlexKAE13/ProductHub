package ru.eltex.producthub.artifact.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "artifact_version")
public class ArtifactVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id")
    private Artifact artifact;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    private String comment;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    protected ArtifactVersion() {
    }

    public ArtifactVersion(Artifact artifact, int versionNo, String comment, String uploadedBy) {
        this.artifact = artifact;
        this.versionNo = versionNo;
        this.comment = comment;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getComment() {
        return comment;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
