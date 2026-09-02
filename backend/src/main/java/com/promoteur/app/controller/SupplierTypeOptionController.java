package com.promoteur.app.controller;

import com.promoteur.app.dto.SupplierTypeOptionRequest;
import com.promoteur.app.entity.SupplierTypeOption;
import com.promoteur.app.service.SupplierTypeOptionService;
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
@RequestMapping("/api/supplier-types")
@RequiredArgsConstructor
public class SupplierTypeOptionController {

    private final SupplierTypeOptionService supplierTypeOptionService;

    @GetMapping
    public Page<SupplierTypeOption> findAll(Pageable pageable) {
        return supplierTypeOptionService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public SupplierTypeOption findById(@PathVariable Long id) {
        return supplierTypeOptionService.findById(id);
    }

    @PostMapping
    public SupplierTypeOption create(@Valid @RequestBody SupplierTypeOptionRequest request) {
        return supplierTypeOptionService.create(request);
    }

    @PutMapping("/{id}")
    public SupplierTypeOption update(@PathVariable Long id, @Valid @RequestBody SupplierTypeOptionRequest request) {
        return supplierTypeOptionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierTypeOptionService.delete(id);
    }
}
