package com.promoteur.app.controller;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.entity.Expense;
import com.promoteur.app.service.ExpenseService;
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
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public Page<Expense> findAll(Pageable pageable) {
        return expenseService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public Expense findById(@PathVariable Long id) {
        return expenseService.findById(id);
    }

    @PostMapping
    public Expense create(@Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(request);
    }

    @PutMapping("/{id}")
    public Expense update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        expenseService.delete(id);
    }

    @GetMapping("/by-category/{categoryId}")
    public Page<Expense> findByCategory(@PathVariable Long categoryId, Pageable pageable) {
        return expenseService.findByCategory(categoryId, pageable);
    }

    @GetMapping("/by-project/{projectId}")
    public Page<Expense> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return expenseService.findByProject(projectId, pageable);
    }
}
