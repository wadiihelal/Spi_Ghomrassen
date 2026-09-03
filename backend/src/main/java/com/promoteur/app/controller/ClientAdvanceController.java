package com.promoteur.app.controller;

import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.response.ClientAdvanceResponse;
import com.promoteur.app.service.ClientAdvanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/client-advances")
@RequiredArgsConstructor
public class ClientAdvanceController {

    private final ClientAdvanceService clientAdvanceService;

    /**
     * Filtered, paginated list. Every parameter is optional (PERF-02).
     */
    @GetMapping
    public Page<ClientAdvanceResponse> findAll(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long apartmentId,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return clientAdvanceService.findAll(new ListFilter(projectId, clientId, supplierId, categoryId, apartmentId,
                paymentStatus, paymentMethod, dateFrom, dateTo, search), pageable);
    }

    @GetMapping("/{id}")
    public ClientAdvanceResponse findById(@PathVariable Long id) {
        return clientAdvanceService.findById(id);
    }

    @PostMapping
    public ClientAdvanceResponse create(@Valid @RequestBody ClientAdvanceRequest request) {
        return clientAdvanceService.create(request);
    }

    @PutMapping("/{id}")
    public ClientAdvanceResponse update(@PathVariable Long id, @Valid @RequestBody ClientAdvanceRequest request) {
        return clientAdvanceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        clientAdvanceService.delete(id);
    }

    /** @deprecated use the query parameters on {@code GET} instead; kept for one release. */
    @Deprecated(forRemoval = true)
    @GetMapping("/by-client/{clientId}")
    public Page<ClientAdvanceResponse> findByClient(@PathVariable Long clientId, Pageable pageable) {
        return clientAdvanceService.findByClient(clientId, pageable);
    }

    /** @deprecated use the query parameters on {@code GET} instead; kept for one release. */
    @Deprecated(forRemoval = true)
    @GetMapping("/by-project/{projectId}")
    public Page<ClientAdvanceResponse> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return clientAdvanceService.findByProject(projectId, pageable);
    }
}
