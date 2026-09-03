package com.promoteur.app.repository;

import com.promoteur.app.dto.report.ClientStatementDto;
import com.promoteur.app.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {
    /** One query for the list endpoint: the associations the response needs are joined. */
    @EntityGraph(attributePaths = {"project"})
    @Override
    Page<Client> findAll(Pageable pageable);

    List<Client> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    /**
     * PERF-01: one query for a page of client statements. The two collected totals are
     * correlated subqueries, so no fan-out and no per-client round trip — this replaced three
     * queries per client, over 900 at 300 clients.
     */
    @Query(value = """
            select new com.promoteur.app.dto.report.ClientStatementDto(
                c.id,
                c.fullName,
                coalesce((select sum(p.totalAmount) from ClientPurchase p
                          where p.client = c
                            and (:projectId is null or p.project.id = :projectId)
                            and (:from is null or p.purchaseDate >= :from)
                            and (:to is null or p.purchaseDate <= :to)), 0),
                coalesce((select sum(a.amount) from ClientAdvance a
                          where a.client = c
                            and (:projectId is null or a.project.id = :projectId)
                            and (:from is null or a.advanceDate >= :from)
                            and (:to is null or a.advanceDate <= :to)), 0)
                    + coalesce((select sum(p2.paidAmount) from ClientPurchase p2
                          where p2.client = c
                            and (:projectId is null or p2.project.id = :projectId)
                            and (:from is null or p2.purchaseDate >= :from)
                            and (:to is null or p2.purchaseDate <= :to)), 0))
            from Client c
            where (:projectId is null or c.project.id = :projectId)
              and (:clientId is null or c.id = :clientId)
            """,
            countQuery = """
            select count(c) from Client c
            where (:projectId is null or c.project.id = :projectId)
              and (:clientId is null or c.id = :clientId)
            """)
    Page<ClientStatementDto> statements(@Param("clientId") Long clientId,
                                        @Param("projectId") Long projectId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        Pageable pageable);

}
