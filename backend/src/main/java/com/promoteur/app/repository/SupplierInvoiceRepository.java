package com.promoteur.app.repository;

import com.promoteur.app.entity.SupplierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
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
