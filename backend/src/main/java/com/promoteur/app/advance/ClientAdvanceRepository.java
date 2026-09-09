package com.promoteur.app.advance;

import com.promoteur.app.apartment.ApartmentAdvanceTotal;
import com.promoteur.app.report.AmountByLabelDto;
import com.promoteur.app.report.CountAndTotal;
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

public interface ClientAdvanceRepository extends JpaRepository<ClientAdvance, Long>, JpaSpecificationExecutor<ClientAdvance> {
    /**
     * Filtered list endpoint (PERF-02). Redeclared from {@link JpaSpecificationExecutor} so the
     * entity graph applies here as well: filtering must not reintroduce the N+1.
     */
    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    @Override
    Page<ClientAdvance> findAll(Specification<ClientAdvance> specification, Pageable pageable);

    /**
     * One query for the list endpoint: the associations the response needs are joined.
     */
    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    @Override
    Page<ClientAdvance> findAll(Pageable pageable);

    List<ClientAdvance> findByClientId(Long clientId);

    /**
     * A buyer's payments in date order, for their statement of account (UX-06).
     */
    @EntityGraph(attributePaths = {"project", "apartment"})
    List<ClientAdvance> findByClientIdOrderByAdvanceDateAsc(Long clientId);

    List<ClientAdvance> findByProjectId(Long projectId);

    List<ClientAdvance> findByApartmentId(Long apartmentId);

    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    Page<ClientAdvance> findByClientId(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "project", "apartment"})
    Page<ClientAdvance> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "project", "apartment"})
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

    /**
     * Advance totals for a set of apartments, in one query. Replaces the per-row lookup that
     * made every page of sale contracts issue an extra query per line (PERF-03).
     */
    @Query("""
            select new com.promoteur.app.apartment.ApartmentAdvanceTotal(a.apartment.id, sum(a.amount))
            from ClientAdvance a
            where a.apartment.id in :apartmentIds
            group by a.apartment.id
            """)
    List<ApartmentAdvanceTotal> sumAmountByApartmentIds(@Param("apartmentIds") Collection<Long> apartmentIds);

    /**
     * Advance count and total for the dashboard summary, in one query.
     */
    @Query("""
            select new com.promoteur.app.report.CountAndTotal(count(a), coalesce(sum(a.amount), 0))
            from ClientAdvance a
            where (:projectId is null or a.project.id = :projectId)
              and (:from is null or a.advanceDate >= :from)
              and (:to is null or a.advanceDate <= :to)
            """)
    CountAndTotal countAndTotal(@Param("projectId") Long projectId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * Advance totals grouped by payment method, for the advances screen's KPI strip.
     */
    @Query(value = """
            select new com.promoteur.app.report.AmountByLabelDto(
                cast(a.paymentMethod as string), sum(a.amount))
            from ClientAdvance a
            where (:projectId is null or a.project.id = :projectId)
              and (:from is null or a.advanceDate >= :from)
              and (:to is null or a.advanceDate <= :to)
            group by a.paymentMethod
            order by sum(a.amount) desc
            """,
            countQuery = """
                    select count(distinct a.paymentMethod)
                    from ClientAdvance a
                    where (:projectId is null or a.project.id = :projectId)
                      and (:from is null or a.advanceDate >= :from)
                      and (:to is null or a.advanceDate <= :to)
                    """)
    Page<AmountByLabelDto> sumByPaymentMethod(@Param("projectId") Long projectId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to,
                                              Pageable pageable);
}
