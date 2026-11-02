package ru.eltex.producthub.task.domain;

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

import ru.eltex.producthub.security.User;

/**
 * Запись журнала изменений своей доски. Текст собирается из ключа boardEvent.[kind] и аргументов, названия
 * колонок и карточек сохраняются как были на момент события.
 */
@Entity
@Table(name = "task_event")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "task_id")
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(length = 500)
    private String subject;

    @Column(length = 300)
    private String arg1;

    @Column(length = 300)
    private String arg2;

    @Column(length = 16)
    private String color1;

    @Column(length = 16)
    private String color2;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TaskEvent() {
    }

    public TaskEvent(Long boardId, Long taskId, User actor, String kind, String subject, String arg1, String arg2) {
        this.boardId = boardId;
        this.taskId = taskId;
        this.actor = actor;
        this.kind = kind;
        this.subject = cut(subject, 500);
        this.arg1 = cut(arg1, 300);
        this.arg2 = cut(arg2, 300);
        this.createdAt = LocalDateTime.now();
    }

    public TaskEvent(Long boardId, Long taskId, User actor, String kind, String subject, String arg1, String arg2,
                     String color1, String color2) {
        this(boardId, taskId, actor, kind, subject, arg1, arg2);
        this.color1 = color1;
        this.color2 = color2;
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public User getActor() {
        return actor;
    }

    public String getKind() {
        return kind;
    }

    public String getSubject() {
        return subject;
    }

    public String getArg1() {
        return arg1;
    }

    public String getArg2() {
        return arg2;
    }

    public String getColor1() {
        return color1;
    }

    public String getColor2() {
        return color2;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
