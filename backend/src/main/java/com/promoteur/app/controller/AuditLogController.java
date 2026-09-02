package com.promoteur.app.controller;

import com.promoteur.app.entity.AuditLog;
import com.promoteur.app.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/by-entity/{entityType}/{entityId}")
    public Page<AuditLog> findByEntity(@PathVariable String entityType, @PathVariable Long entityId, Pageable pageable) {
        return auditLogService.findByEntity(entityType, entityId, pageable);
    }
}
