package com.promoteur.app.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier type option lifecycle operations.
 */
public interface SupplierTypeOptionService {

    /**
     * Returns supplier type options using the requested pagination.
     */
    Page<SupplierTypeOptionResponse> findAll(Pageable pageable);

    /**
     * Returns a single supplier type option by identifier.
     */
    SupplierTypeOptionResponse findById(Long id);

    /**
     * Creates a new supplier type option.
     */
    SupplierTypeOptionResponse create(SupplierTypeOptionRequest request);

    /**
     * Updates an existing supplier type option.
     */
    SupplierTypeOptionResponse update(Long id, SupplierTypeOptionRequest request);

    /**
     * Deletes a supplier type option by identifier.
     */
    void delete(Long id);
}
