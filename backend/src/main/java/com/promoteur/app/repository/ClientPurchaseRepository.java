package com.promoteur.app.repository;

import com.promoteur.app.entity.ClientPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientPurchaseRepository extends JpaRepository<ClientPurchase, Long> {
    List<ClientPurchase> findByClientId(Long clientId);

    List<ClientPurchase> findByProjectId(Long projectId);

    Page<ClientPurchase> findByClientId(Long clientId, Pageable pageable);

    Page<ClientPurchase> findByProjectId(Long projectId, Pageable pageable);

    Page<ClientPurchase> findByApartmentId(Long apartmentId, Pageable pageable);

    Optional<ClientPurchase> findByReference(String reference);

    Optional<ClientPurchase> findByApartmentId(Long apartmentId);

    /**
     * Purchases inside a report scope. Every parameter is optional: {@code null} does not
     * restrict.
     */
    @Query("""
            select p from ClientPurchase p
            where (:clientId is null or p.client.id = :clientId)
              and (:projectId is null or p.project.id = :projectId)
              and (:from is null or p.purchaseDate >= :from)
              and (:to is null or p.purchaseDate <= :to)
            """)
    List<ClientPurchase> findForReport(@Param("clientId") Long clientId,
                                       @Param("projectId") Long projectId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
