package com.promoteur.app.controller;

import com.promoteur.app.dto.ExpenseCategoryRequest;
import com.promoteur.app.dto.response.ExpenseCategoryResponse;
import com.promoteur.app.service.ExpenseCategoryService;
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
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    public Page<ExpenseCategoryResponse> findAll(Pageable pageable) {
        return expenseCategoryService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ExpenseCategoryResponse findById(@PathVariable Long id) {
        return expenseCategoryService.findById(id);
    }

    @PostMapping
    public ExpenseCategoryResponse create(@Valid @RequestBody ExpenseCategoryRequest request) {
        return expenseCategoryService.create(request);
    }

    @PutMapping("/{id}")
    public ExpenseCategoryResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return expenseCategoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        expenseCategoryService.delete(id);
    }
}
