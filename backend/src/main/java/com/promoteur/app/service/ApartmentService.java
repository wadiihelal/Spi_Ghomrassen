package com.promoteur.app.service;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Application service responsible for apartment lifecycle operations.
 */
public interface ApartmentService {

    /**
     * Returns apartments using the requested pagination.
     */
    Page<ApartmentResponse> findAll(ListFilter filter, Pageable pageable);

    /**
     * Returns a single apartment by identifier.
     */
    ApartmentResponse findById(Long id);

    /**
     * Creates a new apartment.
     */
    ApartmentResponse create(ApartmentRequest request);

    /**
     * Updates an existing apartment.
     */
    ApartmentResponse update(Long id, ApartmentRequest request);

    /**
     * Deletes an apartment by identifier.
     */
    void delete(Long id);

    /**
     * Returns apartments for a given project.
     */
    Page<ApartmentResponse> findByProject(Long projectId, Pageable pageable);
}
