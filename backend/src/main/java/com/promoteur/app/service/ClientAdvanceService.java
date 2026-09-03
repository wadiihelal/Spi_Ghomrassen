package com.promoteur.app.service;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.response.ClientAdvanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing client advance management operations.
 */
public interface ClientAdvanceService {

    /**
     * Returns client advances using the requested pagination.
     */
    Page<ClientAdvanceResponse> findAll(ListFilter filter, Pageable pageable);

    /**
     * Returns a single client advance by identifier.
     */
    ClientAdvanceResponse findById(Long id);

    /**
     * Creates a new client advance.
     */
    ClientAdvanceResponse create(ClientAdvanceRequest request);

    /**
     * Updates an existing client advance.
     */
    ClientAdvanceResponse update(Long id, ClientAdvanceRequest request);

    /**
     * Deletes a client advance by identifier.
     */
    void delete(Long id);

    /**
     * Returns all advances for a given client.
     */
    Page<ClientAdvanceResponse> findByClient(Long clientId, Pageable pageable);

    /**
     * Returns all advances for a given project.
     */
    Page<ClientAdvanceResponse> findByProject(Long projectId, Pageable pageable);
}
