package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.entity.Expense;
import com.promoteur.app.entity.ExpenseCategory;
import com.promoteur.app.entity.Project;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.repository.ExpenseRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    @Override
    public Page<Expense> findAll(final Pageable pageable) {
        return this.expenseRepository.findAll(pageable);
    }

    @Override
    public Expense findById(final Long id) {
        return this.expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id " + id));
    }

    @Override
    public Expense create(final ExpenseRequest request) {
        final Expense expense = new Expense();
        this.map(expense, request);

        Expense saved = this.expenseRepository.save(expense);
        saved = this.finalizeGeneratedReference(saved, request.getReference());

        this.auditLogService.create("EXPENSE", saved.getId(), "CREATE", "Depense " + saved.getDescription() + " enregistree.");
        return saved;
    }

    @Override
    public Expense update(final Long id, final ExpenseRequest request) {
        final Expense expense = this.findById(id);
        this.map(expense, request);
        final Expense saved = this.expenseRepository.save(expense);
        this.auditLogService.create("EXPENSE", saved.getId(), "UPDATE", "Depense " + saved.getDescription() + " modifiee.");
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final Expense expense = this.findById(id);
        this.expenseRepository.delete(expense);
        this.auditLogService.create("EXPENSE", id, "DELETE", "Depense " + expense.getDescription() + " supprimee.");
    }

    @Override
    public Page<Expense> findByCategory(final Long categoryId, final Pageable pageable) {
        return this.expenseRepository.findByCategoryId(categoryId, pageable);
    }

    @Override
    public Page<Expense> findByProject(final Long projectId, final Pageable pageable) {
        return this.expenseRepository.findByProjectId(projectId, pageable);
    }

    private void map(final Expense expense, final ExpenseRequest request) {
        this.validateAmounts(request);

        final ExpenseCategory category = this.expenseCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found with id " + request.getCategoryId()));
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = this.supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id " + request.getSupplierId()));
        }

        expense.setReference(this.resolveReference(expense, request.getReference()));
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(request.getDescription());
        expense.setAmountHt(request.getAmountHt());
        expense.setVatAmount(this.normalizedVatAmount(request));
        expense.setAmountTtc(request.getAmountTtc());
        expense.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : com.promoteur.app.enums.PaymentMethod.OTHER);
        expense.setDocumentNumber(request.getDocumentNumber());
        expense.setAttachmentName(request.getAttachmentName());
        expense.setAttachmentUrl(request.getAttachmentUrl());
        expense.setNotes(request.getNotes());
        expense.setCategory(category);
        expense.setProject(project);
        expense.setSupplier(supplier);
    }

    private void validateAmounts(final ExpenseRequest request) {
        final BigDecimal expectedAmountTtc = request.getAmountHt().add(this.normalizedVatAmount(request));
        if (expectedAmountTtc.compareTo(request.getAmountTtc()) != 0) {
            throw new IllegalArgumentException("amountTtc must be equal to amountHt + vatAmount");
        }
    }

    private BigDecimal normalizedVatAmount(final ExpenseRequest request) {
        return request.getVatAmount() == null ? BigDecimal.ZERO : request.getVatAmount();
    }

    private String resolveReference(final Expense expense, final String requestedReference) {
        if (StringUtils.hasText(requestedReference)) {
            return requestedReference.trim();
        }
        if (StringUtils.hasText(expense.getReference())) {
            return expense.getReference();
        }
        if (expense.getId() != null) {
            return this.formatReference(expense.getId());
        }
        return "DEP-TMP-" + UUID.randomUUID();
    }

    private Expense finalizeGeneratedReference(final Expense expense, final String requestedReference) {
        if (StringUtils.hasText(requestedReference)) {
            return expense;
        }

        final String finalReference = this.formatReference(expense.getId());
        if (finalReference.equals(expense.getReference())) {
            return expense;
        }

        expense.setReference(finalReference);
        return this.expenseRepository.save(expense);
    }

    private String formatReference(final Long id) {
        return String.format("DEP-%05d", id);
    }
}
