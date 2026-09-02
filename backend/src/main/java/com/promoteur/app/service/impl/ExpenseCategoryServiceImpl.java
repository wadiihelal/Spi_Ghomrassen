package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ExpenseCategoryRequest;
import com.promoteur.app.entity.ExpenseCategory;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ExpenseCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AuditLogService auditLogService;

    @Override
    public Page<ExpenseCategory> findAll(final Pageable pageable) {
        return this.expenseCategoryRepository.findAll(pageable);
    }

    @Override
    public ExpenseCategory findById(final Long id) {
        return this.expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found with id " + id));
    }

    @Override
    public ExpenseCategory create(final ExpenseCategoryRequest request) {
        final ExpenseCategory category = new ExpenseCategory();
        this.map(category, request);
        final ExpenseCategory saved = this.expenseCategoryRepository.save(category);
        this.auditLogService.create("EXPENSE_CATEGORY", saved.getId(), "CREATE", "Categorie de depense " + saved.getName() + " creee.");
        return saved;
    }

    @Override
    public ExpenseCategory update(final Long id, final ExpenseCategoryRequest request) {
        final ExpenseCategory category = this.findById(id);
        this.map(category, request);
        final ExpenseCategory saved = this.expenseCategoryRepository.save(category);
        this.auditLogService.create("EXPENSE_CATEGORY", saved.getId(), "UPDATE", "Categorie de depense " + saved.getName() + " modifiee.");
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final ExpenseCategory category = this.findById(id);
        this.expenseCategoryRepository.delete(category);
        this.auditLogService.create("EXPENSE_CATEGORY", id, "DELETE", "Categorie de depense " + category.getName() + " supprimee.");
    }

    private void map(final ExpenseCategory category, final ExpenseCategoryRequest request) {
        category.setName(request.getName());
    }
}
