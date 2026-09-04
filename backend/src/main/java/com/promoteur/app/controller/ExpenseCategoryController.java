package com.promoteur.app.controller;

import com.promoteur.app.dto.ExpenseCategoryRequest;
import com.promoteur.app.dto.response.ExpenseCategoryResponse;
import com.promoteur.app.service.ExpenseCategoryService;
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

@Tag(name = "Catégories de dépense", description = "Référentiel des catégories.")
@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @Operation(summary = "Liste paginée et filtrée")
    @GetMapping
    public Page<ExpenseCategoryResponse> findAll(Pageable pageable) {
        return expenseCategoryService.findAll(pageable);
    }

    @Operation(summary = "Détail par identifiant")
    @GetMapping("/{id}")
    public ExpenseCategoryResponse findById(@PathVariable Long id) {
        return expenseCategoryService.findById(id);
    }

    @Operation(summary = "Création")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ExpenseCategoryResponse> create(@Valid @RequestBody ExpenseCategoryRequest request) {
        ExpenseCategoryResponse created = expenseCategoryService.create(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @Operation(summary = "Modification")
    @PutMapping("/{id}")
    public ExpenseCategoryResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return expenseCategoryService.update(id, request);
    }

    @Operation(summary = "Suppression")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        expenseCategoryService.delete(id);
    }

    /** Location of a freshly created resource, for the 201 response (API-02). */
    private URI locationOf(final Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
