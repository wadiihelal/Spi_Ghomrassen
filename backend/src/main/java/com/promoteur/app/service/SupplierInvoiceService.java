package com.promoteur.app.service;

import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.entity.SupplierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier invoice lifecycle operations.
 */
public interface SupplierInvoiceService {

    /**
     * Returns supplier invoices using the requested pagination.
     */
    Page<SupplierInvoice> findAll(Pageable pageable);

    /**
     * Returns a single supplier invoice by identifier.
     */
    SupplierInvoice findById(Long id);

    /**
     * Creates a new supplier invoice.
     */
    SupplierInvoice create(SupplierInvoiceRequest request);

    /**
     * Updates an existing supplier invoice.
     */
    SupplierInvoice update(Long id, SupplierInvoiceRequest request);

    /**
     * Deletes a supplier invoice by identifier.
     */
    void delete(Long id);

    /**
     * Returns invoices for a given project.
     */
    Page<SupplierInvoice> findByProject(Long projectId, Pageable pageable);

    /**
     * Returns invoices for a given supplier.
     */
    Page<SupplierInvoice> findBySupplier(Long supplierId, Pageable pageable);
}
