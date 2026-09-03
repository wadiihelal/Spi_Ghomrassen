package com.promoteur.app.service;

import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.dto.response.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier lifecycle operations.
 */
public interface SupplierService {

    /**
     * Returns suppliers using the requested pagination.
     */
    Page<SupplierResponse> findAll(Pageable pageable);

    /**
     * Returns a single supplier by identifier.
     */
    SupplierResponse findById(Long id);

    /**
     * Creates a new supplier.
     */
    SupplierResponse create(SupplierRequest request);

    /**
     * Updates an existing supplier.
     */
    SupplierResponse update(Long id, SupplierRequest request);

    /**
     * Deletes a supplier by identifier.
     */
    void delete(Long id);
}
