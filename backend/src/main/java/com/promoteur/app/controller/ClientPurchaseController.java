package com.promoteur.app.controller;

import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.response.ClientPurchaseResponse;
import com.promoteur.app.service.ClientPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-purchases")
@RequiredArgsConstructor
public class ClientPurchaseController {

    private final ClientPurchaseService clientPurchaseService;

    @GetMapping
    public Page<ClientPurchaseResponse> findAll(Pageable pageable) {
        return clientPurchaseService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ClientPurchaseResponse findById(@PathVariable Long id) {
        return clientPurchaseService.findById(id);
    }

    @PostMapping
    public ClientPurchaseResponse create(@Valid @RequestBody ClientPurchaseRequest request) {
        return clientPurchaseService.create(request);
    }

    @PutMapping("/{id}")
    public ClientPurchaseResponse update(@PathVariable Long id, @Valid @RequestBody ClientPurchaseRequest request) {
        return clientPurchaseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        clientPurchaseService.delete(id);
    }

    @GetMapping("/by-client/{clientId}")
    public Page<ClientPurchaseResponse> findByClient(@PathVariable Long clientId, Pageable pageable) {
        return clientPurchaseService.findByClient(clientId, pageable);
    }

    @GetMapping("/by-project/{projectId}")
    public Page<ClientPurchaseResponse> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return clientPurchaseService.findByProject(projectId, pageable);
    }
}
