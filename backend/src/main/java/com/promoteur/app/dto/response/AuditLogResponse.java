package com.promoteur.app.dto.response;

import java.time.LocalDateTime;

/**
 * One audit trail line. {@code createdAt} is the moment the action happened, so unlike the other
 * resources it belongs to the contract rather than being an internal column.
 */
public record AuditLogResponse(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String summary,
        String actor,
        LocalDateTime createdAt
) {
}
