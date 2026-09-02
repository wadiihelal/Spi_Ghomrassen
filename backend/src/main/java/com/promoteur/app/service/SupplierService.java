package com.promoteur.app.service;

import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier lifecycle operations.
 */
public interface SupplierService {

    /**
     * Returns suppliers using the requested pagination.
     */
    Page<Supplier> findAll(Pageable pageable);

    /**
     * Returns a single supplier by identifier.
     */
    Supplier findById(Long id);

    /**
     * Creates a new supplier.
     */
    Supplier create(SupplierRequest request);

    /**
     * Updates an existing supplier.
     */
    Supplier update(Long id, SupplierRequest request);

    /**
     * Deletes a supplier by identifier.
     */
    void delete(Long id);
}
