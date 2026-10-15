package ru.eltex.producthub.importer;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Журнал одного запуска импорта: сколько создано, обновлено, пропущено, ошибки по строкам. */
@Entity
@Table(name = "import_run")
public class ImportRun {

    private static final int LOG_LIMIT = 19_500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind;

    @Column(name = "started_by", nullable = false)
    private String startedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private int created;
    private int updated;
    private int skipped;
    private int errors;

    @Column(length = 20000)
    private String log;

    protected ImportRun() {
    }

    public ImportRun(String kind, String startedBy) {
        this.kind = kind;
        this.startedBy = startedBy;
        this.startedAt = LocalDateTime.now();
    }

    public void finish(int created, int updated, int skipped, int errors, String fullLog) {
        this.finishedAt = LocalDateTime.now();
        this.created = created;
        this.updated = updated;
        this.skipped = skipped;
        this.errors = errors;
        this.log = fullLog.length() > LOG_LIMIT ? fullLog.substring(0, LOG_LIMIT) + "\n... (журнал обрезан)" : fullLog;
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getErrors() {
        return errors;
    }

    public String getLog() {
        return log;
    }
}
