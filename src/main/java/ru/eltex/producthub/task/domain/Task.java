package ru.eltex.producthub.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

import ru.eltex.producthub.security.User;
import ru.eltex.producthub.sku.domain.Sku;

/** Задача (SPEC.md 4.9). */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** Логин пользователя или SYSTEM. */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority = TaskPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type")
    private RelatedType relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "related_label")
    private String relatedLabel;

    @Column(name = "related_url")
    private String relatedUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    @Column(name = "source_event")
    private String sourceEvent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Своя доска; пусто - системная доска "Входящие запросы". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private TaskBoard board;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    protected Task() {
    }

    public Task(String title, TaskType type, User assignee, String createdBy) {
        this.title = title;
        this.type = type;
        this.assignee = assignee;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void moveTo(TaskStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
        this.closedAt = newStatus == TaskStatus.DONE ? this.updatedAt : null;
    }

    /** Отмечает, что карточку правили (название, описание, приоритет). */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public void archive() {
        this.archived = true;
        this.archivedAt = LocalDateTime.now();
    }

    public void unarchive() {
        this.archived = false;
        this.archivedAt = null;
    }

    public TaskBoard getBoard() {
        return board;
    }

    public void setBoard(TaskBoard board) {
        this.board = board;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && status != TaskStatus.DONE && dueDate.isBefore(today);
    }

    public Long getId() {
        return id;
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

    public TaskType getType() {
        return type;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
        this.updatedAt = LocalDateTime.now();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public RelatedType getRelatedType() {
        return relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public String getRelatedLabel() {
        return relatedLabel;
    }

    public String getRelatedUrl() {
        return relatedUrl;
    }

    public void setRelated(RelatedType type, Long id, String label, String url) {
        this.relatedType = type;
        this.relatedId = id;
        this.relatedLabel = label;
        this.relatedUrl = url;
    }

    public Sku getSku() {
        return sku;
    }

    public void setSku(Sku sku) {
        this.sku = sku;
    }

    public String getSourceEvent() {
        return sourceEvent;
    }

    public void setSourceEvent(String sourceEvent) {
        this.sourceEvent = sourceEvent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }
}
