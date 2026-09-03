package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.VatAmounts;
import com.promoteur.app.dto.response.ExpenseResponse;
import com.promoteur.app.entity.Expense;
import com.promoteur.app.entity.ExpenseCategory;
import com.promoteur.app.entity.Project;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.ExpenseMapper;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.repository.ExpenseRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ExpenseService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.ReferenceGeneratorService;
import com.promoteur.app.service.VatCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service
@Transactional
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final ExpenseMapper expenseMapper;
    private final VatCalculationService vatCalculationService;
    private final ReferenceGeneratorService referenceGeneratorService;

    @Override
    public Page<ExpenseResponse> findAll(final Pageable pageable) {
        return this.expenseRepository.findAll(pageable).map(this.expenseMapper::toResponse);
    }

    @Override
    public ExpenseResponse findById(final Long id) {
        return this.expenseMapper.toResponse(this.entity(id));
    }

    /** Loads the persisted Expense, for the write paths that need the entity itself. */
    private Expense entity(final Long id) {
        return this.expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id " + id));
    }

    @Override
    public ExpenseResponse create(final ExpenseRequest request) {
        final Expense expense = new Expense();
        this.map(expense, request);

        final Expense saved = this.expenseRepository.save(expense);

        this.auditLogService.create("EXPENSE", saved.getId(), "CREATE",
                this.messageService.get("audit.expense.created", saved.getDescription()));
        return this.expenseMapper.toResponse(saved);
    }

    @Override
    public ExpenseResponse update(final Long id, final ExpenseRequest request) {
        final Expense expense = this.entity(id);
        this.map(expense, request);
        final Expense saved = this.expenseRepository.save(expense);
        this.auditLogService.create("EXPENSE", saved.getId(), "UPDATE",
                this.messageService.get("audit.expense.updated", saved.getDescription()));
        return this.expenseMapper.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final Expense expense = this.entity(id);
        final String description = expense.getDescription();
        this.expenseRepository.delete(expense);
        this.auditLogService.create("EXPENSE", id, "DELETE",
                this.messageService.get("audit.expense.deleted", description));
    }

    @Override
    public Page<ExpenseResponse> findByCategory(final Long categoryId, final Pageable pageable) {
        return this.expenseRepository.findByCategoryId(categoryId, pageable).map(this.expenseMapper::toResponse);
    }

    @Override
    public Page<ExpenseResponse> findByProject(final Long projectId, final Pageable pageable) {
        return this.expenseRepository.findByProjectId(projectId, pageable).map(this.expenseMapper::toResponse);
    }

    private void map(final Expense expense, final ExpenseRequest request) {
        // Le backend est seul maitre du calcul : HT + taux donnent la TVA et le TTC.
        final VatAmounts amounts = this.vatCalculationService.compute(request.getAmountHt(), request.getVatRate());
        this.vatCalculationService.rejectInconsistentDeclaration(amounts, request.getVatAmount(), request.getAmountTtc());

        final ExpenseCategory category = this.expenseCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found with id " + request.getCategoryId()));
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = this.supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id " + request.getSupplierId()));
        }

        expense.setReference(this.resolveReference(expense, request));
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(request.getDescription());
        expense.setAmountHt(request.getAmountHt());
        expense.setVatRate(request.getVatRate());
        expense.setVatAmount(amounts.vatAmount());
        expense.setAmountTtc(amounts.amountTtc());
        expense.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : com.promoteur.app.enums.PaymentMethod.OTHER);
        expense.setDocumentNumber(request.getDocumentNumber());
        expense.setAttachmentName(request.getAttachmentName());
        expense.setAttachmentUrl(request.getAttachmentUrl());
        expense.setNotes(request.getNotes());
        expense.setCategory(category);
        expense.setProject(project);
        expense.setSupplier(supplier);
    }

    /**
     * Resolves the reference before the first save (DATA-03): the caller's own reference when
     * given, the existing one on an update, otherwise a freshly allocated sequential number.
     */
    private String resolveReference(final Expense expense, final ExpenseRequest request) {
        if (StringUtils.hasText(request.getReference())) {
            return request.getReference().trim();
        }
        if (StringUtils.hasText(expense.getReference())) {
            return expense.getReference();
        }
        return this.referenceGeneratorService.nextExpenseReference(request.getExpenseDate());
    }
}
