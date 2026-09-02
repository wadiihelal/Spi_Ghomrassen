package com.promoteur.app.service;

import com.promoteur.app.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Service exposing audit trail creation and retrieval.
 */
public interface AuditLogService {

    /**
     * Persists a new audit log entry, stamped with the acting username.
     */
    AuditLog create(String entityType, Long entityId, String action, String summary);

    /**
     * Returns audit log entries for a given entity.
     */
    Page<AuditLog> findByEntity(String entityType, Long entityId, Pageable pageable);

    /**
     * Returns audit log entries matching the given filters, most recent first. Every filter is
     * optional: a {@code null} or blank value does not restrict the result.
     *
     * @param entityType entity type to restrict to, e.g. {@code EXPENSE}
     * @param actor      acting username to restrict to
     * @param dateFrom   earliest date, inclusive
     * @param dateTo     latest date, inclusive of the whole day
     */
    Page<AuditLog> search(String entityType, String actor, LocalDate dateFrom, LocalDate dateTo, Pageable pageable);
}
