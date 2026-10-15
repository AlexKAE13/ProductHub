package ru.eltex.producthub.common;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Запись истории изменений (SPEC.md 4.10). */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "user_login", nullable = false)
    private String userLogin;

    private String note;

    @Column(nullable = false)
    private LocalDateTime at;

    protected AuditLog() {
    }

    public AuditLog(String entityType, Long entityId, AuditAction action, String field, String oldValue,
                    String newValue, String userLogin, String note, LocalDateTime at) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.field = field;
        this.oldValue = cut(oldValue);
        this.newValue = cut(newValue);
        this.userLogin = userLogin;
        this.note = note;
        this.at = at;
    }

    private static String cut(String value) {
        return value != null && value.length() > 2000 ? value.substring(0, 1997) + "..." : value;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getAt() {
        return at;
    }
}
