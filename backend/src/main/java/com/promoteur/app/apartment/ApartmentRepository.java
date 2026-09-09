package com.promoteur.app.apartment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApartmentRepository extends JpaRepository<Apartment, Long>, JpaSpecificationExecutor<Apartment> {
    /**
     * Filtered list endpoint (PERF-02). Redeclared from {@link JpaSpecificationExecutor} so the
     * entity graph applies here as well: filtering must not reintroduce the N+1.
     */
    @EntityGraph(attributePaths = {"project", "acquirer"})
    @Override
    Page<Apartment> findAll(Specification<Apartment> specification, Pageable pageable);

    /**
     * One query for the list endpoint: the associations the response needs are joined.
     */
    @EntityGraph(attributePaths = {"project", "acquirer"})
    @Override
    Page<Apartment> findAll(Pageable pageable);

    List<Apartment> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"project", "acquirer"})
    Page<Apartment> findByProjectId(Long projectId, Pageable pageable);

    /**
     * Every unit of a project, acquirer joined, for the sales board (UX-05). The board shows the
     * whole stock at once, so it is deliberately unpaginated but bounded by the project.
     */
    @EntityGraph(attributePaths = {"project", "acquirer"})
    @Query("select a from Apartment a where a.project.id = :projectId")
    List<Apartment> findByProjectIdForBoard(@Param("projectId") Long projectId);

    /**
     * The same board across every project.
     */
    @EntityGraph(attributePaths = {"project", "acquirer"})
    @Query("select a from Apartment a")
    List<Apartment> findAllForBoard();

    /**
     * Loads an apartment holding a write lock on its row, so a payment-ceiling check and the
     * write that follows it are atomic against a concurrent one (CONC-01).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Apartment a where a.id = :id")
    Optional<Apartment> findByIdForUpdate(@Param("id") Long id);

    /**
     * Global search (UX-08): by lot number, within the project in scope.
     */
    @EntityGraph(attributePaths = {"project"})
    @Query("""
            select a from Apartment a
            where (:projectId is null or a.project.id = :projectId)
              and lower(a.apartmentNumber) like :pattern
            order by a.apartmentNumber
            """)
    List<Apartment> search(@Param("pattern") String pattern, @Param("projectId") Long projectId, Pageable limit);
}
