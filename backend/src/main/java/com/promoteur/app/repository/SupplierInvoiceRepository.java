package com.promoteur.app.repository;

import com.promoteur.app.entity.SupplierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
    List<SupplierInvoice> findByProjectId(Long projectId);

    List<SupplierInvoice> findBySupplierId(Long supplierId);

    Page<SupplierInvoice> findByProjectId(Long projectId, Pageable pageable);

    Page<SupplierInvoice> findBySupplierId(Long supplierId, Pageable pageable);
}
