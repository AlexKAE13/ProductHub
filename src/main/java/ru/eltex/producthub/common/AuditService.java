package ru.eltex.producthub.common;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public static final String IMPORT_USER = "import";

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String entityType, Long entityId, AuditAction action, String field, Object oldValue,
                       Object newValue, String userLogin, String note) {
        repository.save(new AuditLog(entityType, entityId, action, field, text(oldValue), text(newValue), userLogin,
                note, LocalDateTime.now()));
    }

    /** Пишет запись только если значение действительно изменилось. Возвращает true, если запись сделана. */
    @Transactional
    public boolean recordChange(String entityType, Long entityId, AuditAction action, String field, Object oldValue,
                                Object newValue, String userLogin, String note) {
        if (Objects.equals(text(oldValue), text(newValue))) {
            return false;
        }
        record(entityType, entityId, action, field, oldValue, newValue, userLogin, note);
        return true;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> history(String entityType, Long entityId, int limit) {
        return repository.findHistory(entityType, entityId, PageRequest.of(0, limit));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
