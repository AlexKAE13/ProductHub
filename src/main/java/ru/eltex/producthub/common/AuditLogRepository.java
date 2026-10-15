package ru.eltex.producthub.common;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("select a from AuditLog a where a.entityType = :type and a.entityId = :id order by a.at desc, a.id desc")
    List<AuditLog> findHistory(@Param("type") String type, @Param("id") Long id, Pageable pageable);

    /** Поля, которые правили люди (не импорт). Нужно, чтобы повторный импорт не затирал ручные правки. */
    @Query("select distinct a.entityId, a.field from AuditLog a "
            + "where a.entityType = :type and a.action in ('UPDATE', 'STATUS_CHANGE') and a.userLogin <> :importUser")
    List<Object[]> findManuallyEditedFields(@Param("type") String type, @Param("importUser") String importUser);
}
