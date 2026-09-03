package com.promoteur.app.controller;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.service.SupplierInvoiceService;
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
@RequestMapping("/api/supplier-invoices")
@RequiredArgsConstructor
public class SupplierInvoiceController {

    private final SupplierInvoiceService supplierInvoiceService;

    /**
     * Filtered, paginated list. Every parameter is optional (PERF-02).
     */
    @GetMapping
    public Page<SupplierInvoiceResponse> findAll(
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
        return supplierInvoiceService.findAll(new ListFilter(projectId, clientId, supplierId, categoryId, apartmentId,
                paymentStatus, paymentMethod, dateFrom, dateTo, search), pageable);
    }

    @GetMapping("/{id}")
    public SupplierInvoiceResponse findById(@PathVariable Long id) {
        return supplierInvoiceService.findById(id);
    }

    @PostMapping
    public SupplierInvoiceResponse create(@Valid @RequestBody SupplierInvoiceRequest request) {
        return supplierInvoiceService.create(request);
    }

    @PutMapping("/{id}")
    public SupplierInvoiceResponse update(@PathVariable Long id, @Valid @RequestBody SupplierInvoiceRequest request) {
        return supplierInvoiceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierInvoiceService.delete(id);
    }

    /** @deprecated use the query parameters on {@code GET} instead; kept for one release. */
    @Deprecated(forRemoval = true)
    @GetMapping("/by-project/{projectId}")
    public Page<SupplierInvoiceResponse> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return supplierInvoiceService.findByProject(projectId, pageable);
    }

    /** @deprecated use the query parameters on {@code GET} instead; kept for one release. */
    @Deprecated(forRemoval = true)
    @GetMapping("/by-supplier/{supplierId}")
    public Page<SupplierInvoiceResponse> findBySupplier(@PathVariable Long supplierId, Pageable pageable) {
        return supplierInvoiceService.findBySupplier(supplierId, pageable);
    }
}
