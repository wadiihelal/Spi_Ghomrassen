package com.promoteur.app.service;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing expense lifecycle operations.
 */
public interface ExpenseService {

    /**
     * Returns expenses using the requested pagination.
     */
    Page<Expense> findAll(Pageable pageable);

    /**
     * Returns a single expense by identifier.
     */
    Expense findById(Long id);

    /**
     * Creates a new expense.
     */
    Expense create(ExpenseRequest request);

    /**
     * Updates an existing expense.
     */
    Expense update(Long id, ExpenseRequest request);

    /**
     * Deletes an expense by identifier.
     */
    void delete(Long id);

    /**
     * Returns expenses for a given category.
     */
    Page<Expense> findByCategory(Long categoryId, Pageable pageable);

    /**
     * Returns expenses for a given project.
     */
    Page<Expense> findByProject(Long projectId, Pageable pageable);
}
