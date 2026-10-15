package ru.eltex.producthub.qa.domain;

import java.time.LocalDateTime;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.eltex.producthub.security.User;

/** Вопрос или предложение правки (SPEC.md 4.8). */
@Entity
@EntityListeners(ru.eltex.producthub.common.IndexedChangeListener.class)
@Table(name = "question")
public class Question implements ru.eltex.producthub.common.Indexable {

    @Override
    public String indexType() {
        return "FAQ";
    }

    @Override
    public Long indexId() {
        return id;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "question_text", nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.NEW;

    @Column(length = 4000)
    private String answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private User answeredBy;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "publish_to_faq", nullable = false)
    private boolean publishToFaq;

    /** Технический вопрос: на него может отвечать и сервисный центр. */
    @Column(nullable = false)
    private boolean technical;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Question() {
    }

    public Question(QuestionKind kind, TargetType targetType, Long targetId, User author, String text,
                    boolean technical) {
        this.kind = kind;
        this.targetType = targetType;
        this.targetId = targetId;
        this.author = author;
        this.text = text;
        this.technical = technical;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void resolve(QuestionStatus newStatus, String answer, boolean publishToFaq, User by) {
        this.status = newStatus;
        this.answer = answer;
        this.publishToFaq = publishToFaq;
        this.answeredBy = by;
        this.answeredAt = LocalDateTime.now();
        this.updatedAt = this.answeredAt;
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public QuestionKind getKind() {
        return kind;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public User getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }

    public User getAnsweredBy() {
        return answeredBy;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public boolean isPublishToFaq() {
        return publishToFaq;
    }

    public boolean isTechnical() {
        return technical;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
