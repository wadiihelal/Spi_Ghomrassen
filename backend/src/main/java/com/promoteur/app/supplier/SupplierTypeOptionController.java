package com.promoteur.app.supplier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Types de fournisseur", description = "Référentiel des types.")
@RestController
@RequestMapping("/api/supplier-types")
@RequiredArgsConstructor
public class SupplierTypeOptionController {

    private final SupplierTypeOptionService supplierTypeOptionService;

    @Operation(summary = "Liste paginée et filtrée")
    @GetMapping
    public Page<SupplierTypeOptionResponse> findAll(Pageable pageable) {
        return supplierTypeOptionService.findAll(pageable);
    }

    @Operation(summary = "Détail par identifiant")
    @GetMapping("/{id}")
    public SupplierTypeOptionResponse findById(@PathVariable Long id) {
        return supplierTypeOptionService.findById(id);
    }

    @Operation(summary = "Création")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<SupplierTypeOptionResponse> create(@Valid @RequestBody SupplierTypeOptionRequest request) {
        SupplierTypeOptionResponse created = supplierTypeOptionService.create(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @Operation(summary = "Modification")
    @PutMapping("/{id}")
    public SupplierTypeOptionResponse update(@PathVariable Long id, @Valid @RequestBody SupplierTypeOptionRequest request) {
        return supplierTypeOptionService.update(id, request);
    }

    @Operation(summary = "Suppression")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        supplierTypeOptionService.delete(id);
    }

    /**
     * Location of a freshly created resource, for the 201 response (API-02).
     */
    private URI locationOf(final Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
