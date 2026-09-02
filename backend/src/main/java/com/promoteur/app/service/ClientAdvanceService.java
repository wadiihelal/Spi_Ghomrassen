package com.promoteur.app.service;

import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.entity.ClientAdvance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing client advance management operations.
 */
public interface ClientAdvanceService {

    /**
     * Returns client advances using the requested pagination.
     */
    Page<ClientAdvance> findAll(Pageable pageable);

    /**
     * Returns a single client advance by identifier.
     */
    ClientAdvance findById(Long id);

    /**
     * Creates a new client advance.
     */
    ClientAdvance create(ClientAdvanceRequest request);

    /**
     * Updates an existing client advance.
     */
    ClientAdvance update(Long id, ClientAdvanceRequest request);

    /**
     * Deletes a client advance by identifier.
     */
    void delete(Long id);

    /**
     * Returns all advances for a given client.
     */
    Page<ClientAdvance> findByClient(Long clientId, Pageable pageable);

    /**
     * Returns all advances for a given project.
     */
    Page<ClientAdvance> findByProject(Long projectId, Pageable pageable);
}
