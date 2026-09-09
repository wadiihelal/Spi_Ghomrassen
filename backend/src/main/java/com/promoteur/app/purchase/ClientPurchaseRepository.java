package com.promoteur.app.purchase;

import com.promoteur.app.report.AmountByLabelDto;
import com.promoteur.app.report.PurchaseSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientPurchaseRepository extends JpaRepository<ClientPurchase, Long>, JpaSpecificationExecutor<ClientPurchase> {
    /**
     * Filtered list endpoint (PERF-02). Redeclared from {@link JpaSpecificationExecutor} so the
     * entity graph applies here as well: filtering must not reintroduce the N+1.
     */
    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    @Override
    Page<ClientPurchase> findAll(Specification<ClientPurchase> specification, Pageable pageable);

    /**
     * One query for the list endpoint: the associations the response needs are joined.
     */
    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    @Override
    Page<ClientPurchase> findAll(Pageable pageable);

    List<ClientPurchase> findByClientId(Long clientId);

    /**
     * A buyer's contracts in date order, for their statement of account (UX-06).
     */
    @EntityGraph(attributePaths = {"project", "apartment"})
    List<ClientPurchase> findByClientIdOrderByPurchaseDateAsc(Long clientId);

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
     * Contracts for a set of apartments, in one query, so a page of apartments can be enriched.
     */
    @EntityGraph(attributePaths = {"apartment"})
    List<ClientPurchase> findByApartmentIdIn(Collection<Long> apartmentIds);

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
            select new com.promoteur.app.report.PurchaseSummary(
                count(p), coalesce(sum(p.totalAmount), 0), coalesce(sum(p.paidAmount), 0))
            from ClientPurchase p
            where (:projectId is null or p.project.id = :projectId)
              and (:from is null or p.purchaseDate >= :from)
              and (:to is null or p.purchaseDate <= :to)
            """)
    PurchaseSummary summary(@Param("projectId") Long projectId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    /**
     * Contracted totals grouped by project, for the dashboard's margin per project.
     */
    @Query(value = """
            select new com.promoteur.app.report.AmountByLabelDto(p.project.name, sum(p.totalAmount))
            from ClientPurchase p
            where (:projectId is null or p.project.id = :projectId)
              and (:from is null or p.purchaseDate >= :from)
              and (:to is null or p.purchaseDate <= :to)
            group by p.project.name
            order by sum(p.totalAmount) desc
            """,
            countQuery = """
                    select count(distinct p.project.name)
                    from ClientPurchase p
                    where (:projectId is null or p.project.id = :projectId)
                      and (:from is null or p.purchaseDate >= :from)
                      and (:to is null or p.purchaseDate <= :to)
                    """)
    Page<AmountByLabelDto> sumByProject(@Param("projectId") Long projectId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        Pageable pageable);


    /**
     * Global search (UX-08): by contract reference, within the project in scope.
     */
    @EntityGraph(attributePaths = {"client", "apartment"})
    @Query("""
            select p from ClientPurchase p
            where (:projectId is null or p.project.id = :projectId)
              and lower(p.reference) like :pattern
            order by p.purchaseDate desc
            """)
    List<ClientPurchase> search(@Param("pattern") String pattern, @Param("projectId") Long projectId, Pageable limit);
}
