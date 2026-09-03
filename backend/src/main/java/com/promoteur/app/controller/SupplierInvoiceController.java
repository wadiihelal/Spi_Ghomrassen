package com.promoteur.app.controller;

import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.service.SupplierInvoiceService;
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
@RequestMapping("/api/supplier-invoices")
@RequiredArgsConstructor
public class SupplierInvoiceController {

    private final SupplierInvoiceService supplierInvoiceService;

    @GetMapping
    public Page<SupplierInvoiceResponse> findAll(Pageable pageable) {
        return supplierInvoiceService.findAll(pageable);
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

    @GetMapping("/by-project/{projectId}")
    public Page<SupplierInvoiceResponse> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return supplierInvoiceService.findByProject(projectId, pageable);
    }

    @GetMapping("/by-supplier/{supplierId}")
    public Page<SupplierInvoiceResponse> findBySupplier(@PathVariable Long supplierId, Pageable pageable) {
        return supplierInvoiceService.findBySupplier(supplierId, pageable);
    }
}
