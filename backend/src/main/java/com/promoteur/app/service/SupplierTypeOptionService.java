package com.promoteur.app.service;

import com.promoteur.app.dto.SupplierTypeOptionRequest;
import com.promoteur.app.entity.SupplierTypeOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier type option lifecycle operations.
 */
public interface SupplierTypeOptionService {

    /**
     * Returns supplier type options using the requested pagination.
     */
    Page<SupplierTypeOption> findAll(Pageable pageable);

    /**
     * Returns a single supplier type option by identifier.
     */
    SupplierTypeOption findById(Long id);

    /**
     * Creates a new supplier type option.
     */
    SupplierTypeOption create(SupplierTypeOptionRequest request);

    /**
     * Updates an existing supplier type option.
     */
    SupplierTypeOption update(Long id, SupplierTypeOptionRequest request);

    /**
     * Deletes a supplier type option by identifier.
     */
    void delete(Long id);
}
