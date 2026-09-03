package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.AuditLogResponse;
import com.promoteur.app.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
