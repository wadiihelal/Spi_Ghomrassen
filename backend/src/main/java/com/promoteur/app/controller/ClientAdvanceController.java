package com.promoteur.app.controller;

import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.service.ClientAdvanceService;
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
@RequestMapping("/api/client-advances")
@RequiredArgsConstructor
public class ClientAdvanceController {

    private final ClientAdvanceService clientAdvanceService;

    @GetMapping
    public Page<ClientAdvance> findAll(Pageable pageable) {
        return clientAdvanceService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ClientAdvance findById(@PathVariable Long id) {
        return clientAdvanceService.findById(id);
    }

    @PostMapping
    public ClientAdvance create(@Valid @RequestBody ClientAdvanceRequest request) {
        return clientAdvanceService.create(request);
    }

    @PutMapping("/{id}")
    public ClientAdvance update(@PathVariable Long id, @Valid @RequestBody ClientAdvanceRequest request) {
        return clientAdvanceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        clientAdvanceService.delete(id);
    }

    @GetMapping("/by-client/{clientId}")
    public Page<ClientAdvance> findByClient(@PathVariable Long clientId, Pageable pageable) {
        return clientAdvanceService.findByClient(clientId, pageable);
    }

    @GetMapping("/by-project/{projectId}")
    public Page<ClientAdvance> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return clientAdvanceService.findByProject(projectId, pageable);
    }
}
