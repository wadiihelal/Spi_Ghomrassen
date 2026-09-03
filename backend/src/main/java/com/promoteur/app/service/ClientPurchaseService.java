package com.promoteur.app.service;

import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.response.ClientPurchaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing client purchase management operations.
 */
public interface ClientPurchaseService {

    /**
     * Returns client purchases using the requested pagination.
     */
    Page<ClientPurchaseResponse> findAll(ListFilter filter, Pageable pageable);

    /**
     * Returns a single client purchase by identifier.
     */
    ClientPurchaseResponse findById(Long id);

    /**
     * Creates a new client purchase.
     */
    ClientPurchaseResponse create(ClientPurchaseRequest request);

    /**
     * Updates an existing client purchase.
     */
    ClientPurchaseResponse update(Long id, ClientPurchaseRequest request);

    /**
     * Deletes a client purchase by identifier.
     */
    void delete(Long id);

    /**
     * Returns purchases for a given client.
     */
    Page<ClientPurchaseResponse> findByClient(Long clientId, Pageable pageable);

    /**
     * Returns purchases for a given project.
     */
    Page<ClientPurchaseResponse> findByProject(Long projectId, Pageable pageable);
}
