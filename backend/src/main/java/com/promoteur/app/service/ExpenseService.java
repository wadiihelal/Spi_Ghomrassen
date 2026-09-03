package com.promoteur.app.service;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.response.ExpenseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing expense lifecycle operations.
 */
public interface ExpenseService {

    /**
     * Returns expenses using the requested pagination.
     */
    Page<ExpenseResponse> findAll(ListFilter filter, Pageable pageable);

    /**
     * Returns a single expense by identifier.
     */
    ExpenseResponse findById(Long id);

    /**
     * Creates a new expense.
     */
    ExpenseResponse create(ExpenseRequest request);

    /**
     * Updates an existing expense.
     */
    ExpenseResponse update(Long id, ExpenseRequest request);

    /**
     * Deletes an expense by identifier.
     */
    void delete(Long id);

    /**
     * Returns expenses for a given category.
     */
    Page<ExpenseResponse> findByCategory(Long categoryId, Pageable pageable);

    /**
     * Returns expenses for a given project.
     */
    Page<ExpenseResponse> findByProject(Long projectId, Pageable pageable);
}
