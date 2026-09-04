package com.promoteur.app.controller;

import com.promoteur.app.dto.response.AuditLogResponse;
import com.promoteur.app.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Journal d’audit", description = "Trace de chaque création, modification et suppression.")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Recherche dans le journal (type, acteur, période)")
    @GetMapping
    public Page<AuditLogResponse> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Pageable pageable) {
        return auditLogService.search(entityType, actor, dateFrom, dateTo, pageable);
    }

    @Operation(summary = "Historique d’une entité")
    @GetMapping("/by-entity/{entityType}/{entityId}")
    public Page<AuditLogResponse> findByEntity(@PathVariable String entityType, @PathVariable Long entityId, Pageable pageable) {
        return auditLogService.findByEntity(entityType, entityId, pageable);
    }
}
