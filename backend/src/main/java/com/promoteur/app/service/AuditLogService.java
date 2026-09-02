package com.promoteur.app.service;

import com.promoteur.app.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing audit trail creation and retrieval.
 */
public interface AuditLogService {

    /**
     * Persists a new audit log entry.
     */
    AuditLog create(String entityType, Long entityId, String action, String summary);

    /**
     * Returns audit log entries for a given entity.
     */
    Page<AuditLog> findByEntity(String entityType, Long entityId, Pageable pageable);
}
