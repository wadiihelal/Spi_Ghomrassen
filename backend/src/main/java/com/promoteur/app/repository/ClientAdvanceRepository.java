package com.promoteur.app.repository;

import com.promoteur.app.entity.ClientAdvance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientAdvanceRepository extends JpaRepository<ClientAdvance, Long> {
    List<ClientAdvance> findByClientId(Long clientId);

    List<ClientAdvance> findByProjectId(Long projectId);

    List<ClientAdvance> findByApartmentId(Long apartmentId);

    Page<ClientAdvance> findByClientId(Long clientId, Pageable pageable);

    Page<ClientAdvance> findByProjectId(Long projectId, Pageable pageable);

    Page<ClientAdvance> findByApartmentId(Long apartmentId, Pageable pageable);

    Optional<ClientAdvance> findByReference(String reference);

    /**
     * Advances inside a report scope. Every parameter is optional: {@code null} does not
     * restrict.
     */
    @Query("""
            select a from ClientAdvance a
            where (:clientId is null or a.client.id = :clientId)
              and (:projectId is null or a.project.id = :projectId)
              and (:from is null or a.advanceDate >= :from)
              and (:to is null or a.advanceDate <= :to)
            """)
    List<ClientAdvance> findForReport(@Param("clientId") Long clientId,
                                      @Param("projectId") Long projectId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
