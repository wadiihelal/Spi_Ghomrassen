package com.promoteur.app.expense;

import com.promoteur.app.audit.AuditLogService;
import com.promoteur.app.shared.MessageService;
import com.promoteur.app.shared.ResourceNotFoundException;
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
    private final MessageService messageService;
    private final ExpenseCategoryMapper expenseCategoryMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseCategoryResponse> findAll(final Pageable pageable) {
        return this.expenseCategoryRepository.findAll(pageable).map(this.expenseCategoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseCategoryResponse findById(final Long id) {
        return this.expenseCategoryMapper.toResponse(this.entity(id));
    }

    /**
     * Loads the persisted ExpenseCategory, for the write paths that need the entity itself.
     */
    private ExpenseCategory entity(final Long id) {
        return this.expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found with id " + id));
    }

    @Override
    public ExpenseCategoryResponse create(final ExpenseCategoryRequest request) {
        final ExpenseCategory category = new ExpenseCategory();
        this.map(category, request);
        final ExpenseCategory saved = this.expenseCategoryRepository.save(category);
        this.auditLogService.create("EXPENSE_CATEGORY", saved.getId(), "CREATE",
                this.messageService.get("audit.expenseCategory.created", saved.getName()));
        return this.expenseCategoryMapper.toResponse(saved);
    }

    @Override
    public ExpenseCategoryResponse update(final Long id, final ExpenseCategoryRequest request) {
        final ExpenseCategory category = this.entity(id);
        this.map(category, request);
        final ExpenseCategory saved = this.expenseCategoryRepository.save(category);
        this.auditLogService.create("EXPENSE_CATEGORY", saved.getId(), "UPDATE",
                this.messageService.get("audit.expenseCategory.updated", saved.getName()));
        return this.expenseCategoryMapper.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final ExpenseCategory category = this.entity(id);
        final String name = category.getName();
        this.expenseCategoryRepository.delete(category);
        this.auditLogService.create("EXPENSE_CATEGORY", id, "DELETE",
                this.messageService.get("audit.expenseCategory.deleted", name));
    }

    private void map(final ExpenseCategory category, final ExpenseCategoryRequest request) {
        category.setName(request.getName());
    }
}
