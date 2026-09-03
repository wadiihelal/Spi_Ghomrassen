package com.promoteur.app.repository;

import com.promoteur.app.dto.report.PurchaseSummary;
import com.promoteur.app.entity.ClientPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientPurchaseRepository extends JpaRepository<ClientPurchase, Long> {
    /** One query for the list endpoint: the associations the response needs are joined. */
    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    @Override
    Page<ClientPurchase> findAll(Pageable pageable);

    List<ClientPurchase> findByClientId(Long clientId);

    List<ClientPurchase> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    Page<ClientPurchase> findByClientId(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    Page<ClientPurchase> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "project", "apartment"})
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

    /**
     * Contract count, total contracted and total paid directly, for the dashboard summary, in
     * one query.
     */
    @Query("""
            select new com.promoteur.app.dto.report.PurchaseSummary(
                count(p), coalesce(sum(p.totalAmount), 0), coalesce(sum(p.paidAmount), 0))
            from ClientPurchase p
            where (:projectId is null or p.project.id = :projectId)
              and (:from is null or p.purchaseDate >= :from)
              and (:to is null or p.purchaseDate <= :to)
            """)
    PurchaseSummary summary(@Param("projectId") Long projectId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

}
