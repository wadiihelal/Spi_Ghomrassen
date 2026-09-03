package com.promoteur.app.repository;

import com.promoteur.app.entity.SupplierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long>, JpaSpecificationExecutor<SupplierInvoice> {
    /**
     * Filtered list endpoint (PERF-02). Redeclared from {@link JpaSpecificationExecutor} so the
     * entity graph applies here as well: filtering must not reintroduce the N+1.
     */
    @EntityGraph(attributePaths = {"supplier", "project"})
    @Override
    Page<SupplierInvoice> findAll(Specification<SupplierInvoice> specification, Pageable pageable);

    /** One query for the list endpoint: the associations the response needs are joined. */
    @EntityGraph(attributePaths = {"supplier", "project"})
    @Override
    Page<SupplierInvoice> findAll(Pageable pageable);

    List<SupplierInvoice> findByProjectId(Long projectId);

    List<SupplierInvoice> findBySupplierId(Long supplierId);

    @EntityGraph(attributePaths = {"supplier", "project"})
    Page<SupplierInvoice> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "project"})
    Page<SupplierInvoice> findBySupplierId(Long supplierId, Pageable pageable);
}
