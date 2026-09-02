package com.promoteur.app.service;

import com.promoteur.app.dto.ExpenseCategoryRequest;
import com.promoteur.app.entity.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing expense category lifecycle operations.
 */
public interface ExpenseCategoryService {

    /**
     * Returns expense categories using the requested pagination.
     */
    Page<ExpenseCategory> findAll(Pageable pageable);

    /**
     * Returns a single expense category by identifier.
     */
    ExpenseCategory findById(Long id);

    /**
     * Creates a new expense category.
     */
    ExpenseCategory create(ExpenseCategoryRequest request);

    /**
     * Updates an existing expense category.
     */
    ExpenseCategory update(Long id, ExpenseCategoryRequest request);

    /**
     * Deletes an expense category by identifier.
     */
    void delete(Long id);
}
