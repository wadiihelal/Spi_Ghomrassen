package com.promoteur.app.service.impl;

import com.promoteur.app.dto.response.AuditLogResponse;
import com.promoteur.app.entity.AuditLog;
import com.promoteur.app.mapper.AuditLogMapper;
import com.promoteur.app.repository.AuditLogRepository;
import com.promoteur.app.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /**
     * Actor recorded when no principal can be determined: start-up seeding, scheduled work, and
     * every console action for as long as the application has no authentication (SEC-01).
     */
    private static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    /**
     * Written in its own transaction: a business transaction that rolls back after the audit
     * line was recorded still leaves the trace of the attempt behind.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog create(final String entityType, final Long entityId, final String action, final String summary) {
        final AuditLog auditLog = new AuditLog();
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setAction(action);
        auditLog.setSummary(summary);
        auditLog.setActor(this.resolveActor());
        return this.auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findByEntity(final String entityType, final Long entityId, final Pageable pageable) {
        return this.auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
                .map(this.auditLogMapper::toResponse);
    }

    @Override
    public Page<AuditLogResponse> search(final String entityType, final String actor, final LocalDate dateFrom,
                                        final LocalDate dateTo, final Pageable pageable) {
        final LocalDateTime from = dateFrom == null ? null : dateFrom.atStartOfDay();
        final LocalDateTime to = dateTo == null ? null : dateTo.atTime(LocalTime.MAX);
        return this.auditLogRepository.search(this.blankToNull(entityType), this.blankToNull(actor), from, to, pageable)
                .map(this.auditLogMapper::toResponse);
    }

    /**
     * @return the username behind the current action. There is no authentication yet (SEC-01),
     *         so this is always {@link #SYSTEM_ACTOR}; read the principal here once a login
     *         exists and the rest of the audit trail needs no further change.
     */
    private String resolveActor() {
        return SYSTEM_ACTOR;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
