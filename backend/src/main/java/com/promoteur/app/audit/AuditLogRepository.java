package com.promoteur.app.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId, Pageable pageable);

    /**
     * The journal's search, every filter optional.
     *
     * <p>{@code coalesce} rather than {@code (:param is null or …)}: PostgreSQL refuses a
     * parameter whose type is only ever implied by an {@code IS NULL} and answers
     * {@code could not determine data type of parameter $N}, where H2 accepts it — so the whole
     * test suite stayed green while the journal answered 500 in production (17/09/2026). Every
     * column compared here is {@code NOT NULL}, so a null filter matching the column against
     * itself is always true and the meaning is unchanged.</p>
     */
    @Query("""
            select a from AuditLog a
            where a.entityType = coalesce(:entityType, a.entityType)
              and a.actor = coalesce(:actor, a.actor)
              and a.createdAt >= coalesce(:from, a.createdAt)
              and a.createdAt <= coalesce(:to, a.createdAt)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("entityType") String entityType,
                          @Param("actor") String actor,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}
