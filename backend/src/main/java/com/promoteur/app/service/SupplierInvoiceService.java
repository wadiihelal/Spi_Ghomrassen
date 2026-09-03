package com.promoteur.app.service;

import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier invoice lifecycle operations.
 */
public interface SupplierInvoiceService {

    /**
     * Returns supplier invoices using the requested pagination.
     */
    Page<SupplierInvoiceResponse> findAll(Pageable pageable);

    /**
     * Returns a single supplier invoice by identifier.
     */
    SupplierInvoiceResponse findById(Long id);

    /**
     * Creates a new supplier invoice.
     */
    SupplierInvoiceResponse create(SupplierInvoiceRequest request);

    /**
     * Updates an existing supplier invoice.
     */
    SupplierInvoiceResponse update(Long id, SupplierInvoiceRequest request);

    /**
     * Deletes a supplier invoice by identifier.
     */
    void delete(Long id);

    /**
     * Returns invoices for a given project.
     */
    Page<SupplierInvoiceResponse> findByProject(Long projectId, Pageable pageable);

    /**
     * Returns invoices for a given supplier.
     */
    Page<SupplierInvoiceResponse> findBySupplier(Long supplierId, Pageable pageable);
}
