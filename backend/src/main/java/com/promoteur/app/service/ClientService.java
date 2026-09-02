package com.promoteur.app.service;

import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing client lifecycle operations.
 */
public interface ClientService {

    /**
     * Returns clients using the requested pagination.
     */
    Page<Client> findAll(Pageable pageable);

    /**
     * Returns a single client by identifier.
     */
    Client findById(Long id);

    /**
     * Creates a new client.
     */
    Client create(ClientRequest request);

    /**
     * Updates an existing client.
     */
    Client update(Long id, ClientRequest request);

    /**
     * Deletes a client by identifier.
     */
    void delete(Long id);
}
