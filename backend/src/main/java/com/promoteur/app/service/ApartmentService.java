package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.entity.Apartment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Application service responsible for apartment lifecycle operations.
 */
public interface ApartmentService {

    /**
     * Returns apartments using the requested pagination.
     */
    Page<Apartment> findAll(Pageable pageable);

    /**
     * Returns a single apartment by identifier.
     */
    Apartment findById(Long id);

    /**
     * Creates a new apartment.
     */
    Apartment create(ApartmentRequest request);

    /**
     * Updates an existing apartment.
     */
    Apartment update(Long id, ApartmentRequest request);

    /**
     * Deletes an apartment by identifier.
     */
    void delete(Long id);

    /**
     * Returns apartments for a given project.
     */
    Page<Apartment> findByProject(Long projectId, Pageable pageable);
}
