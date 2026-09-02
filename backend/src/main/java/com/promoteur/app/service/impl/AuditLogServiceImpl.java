package com.promoteur.app.service.impl;

import com.promoteur.app.entity.AuditLog;
import com.promoteur.app.repository.AuditLogRepository;
import com.promoteur.app.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public AuditLog create(final String entityType, final Long entityId, final String action, final String summary) {
        final AuditLog auditLog = new AuditLog();
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setAction(action);
        auditLog.setSummary(summary);
        return this.auditLogRepository.save(auditLog);
    }

    @Override
    public Page<AuditLog> findByEntity(final String entityType, final Long entityId, final Pageable pageable) {
        return this.auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }
}
