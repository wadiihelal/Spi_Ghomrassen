package com.promoteur.app.service;

import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.entity.ClientPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing client purchase management operations.
 */
public interface ClientPurchaseService {

    /**
     * Returns client purchases using the requested pagination.
     */
    Page<ClientPurchase> findAll(Pageable pageable);

    /**
     * Returns a single client purchase by identifier.
     */
    ClientPurchase findById(Long id);

    /**
     * Creates a new client purchase.
     */
    ClientPurchase create(ClientPurchaseRequest request);

    /**
     * Updates an existing client purchase.
     */
    ClientPurchase update(Long id, ClientPurchaseRequest request);

    /**
     * Deletes a client purchase by identifier.
     */
    void delete(Long id);

    /**
     * Returns purchases for a given client.
     */
    Page<ClientPurchase> findByClient(Long clientId, Pageable pageable);

    /**
     * Returns purchases for a given project.
     */
    Page<ClientPurchase> findByProject(Long projectId, Pageable pageable);
}
