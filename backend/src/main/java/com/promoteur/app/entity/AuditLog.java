package com.promoteur.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, length = 1000)
    private String summary;

    /**
     * Who performed the action. {@code system} for start-up and scheduled work, and for every
     * action taken through the console while the application has no authentication (SEC-01).
     */
    @Column(nullable = false)
    private String actor;
}
