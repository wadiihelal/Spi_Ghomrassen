package com.promoteur.app.service.impl;

import com.promoteur.app.dto.InvoicePaymentTotal;
import com.promoteur.app.dto.InvoiceSettlement;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.SupplierPaymentRequest;
import com.promoteur.app.dto.response.PayablesSummaryResponse;
import com.promoteur.app.dto.response.SupplierPaymentResponse;
import com.promoteur.app.entity.SupplierPayment;
import com.promoteur.app.enums.SettlementFilter;
import com.promoteur.app.enums.SettlementStatus;
import com.promoteur.app.repository.SupplierPaymentRepository;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.VatAmounts;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.entity.Project;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.entity.SupplierInvoice;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.SupplierInvoiceMapper;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierInvoiceRepository;
import com.promoteur.app.repository.specification.SupplierInvoiceSpecifications;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.SupplierInvoiceService;
import com.promoteur.app.service.VatCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;


@Service
@Transactional
@RequiredArgsConstructor
public class SupplierInvoiceServiceImpl implements SupplierInvoiceService {

    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final SupplierInvoiceMapper supplierInvoiceMapper;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final VatCalculationService vatCalculationService;

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierInvoiceResponse> findAll(final ListFilter filter, final Pageable pageable) {
        return this.toResponsePage(
                this.supplierInvoiceRepository.findAll(SupplierInvoiceSpecifications.matching(filter), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierInvoiceResponse> findBySettlement(final ListFilter filter,
                                                          final SettlementFilter settlement,
                                                          final Pageable pageable) {
        final Page<SupplierInvoiceResponse> page = this.findAll(filter, pageable);
        if (settlement == null) {
            return page;
        }
        // Settlement is derived, so it cannot be a SQL predicate: the page is narrowed after mapping.
        final List<SupplierInvoiceResponse> kept = page.getContent().stream()
                .filter(invoice -> SupplierInvoiceServiceImpl.matches(invoice, settlement))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(kept, pageable, kept.size());
    }

    /** OVERDUE cuts across the three states: anything still owed whose due date has passed. */
    private static boolean matches(final SupplierInvoiceResponse invoice, final SettlementFilter settlement) {
        if (settlement == SettlementFilter.OVERDUE) {
            return invoice.overdue();
        }
        return invoice.status() != null && invoice.status().name().equals(settlement.name());
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierInvoiceResponse findById(final Long id) {
        final SupplierInvoice invoice = this.entity(id);
        return this.supplierInvoiceMapper.toResponse(invoice, this.settlementOf(invoice, this.paidOn(id)));
    }

    /** Loads the persisted SupplierInvoice, for the write paths that need the entity itself. */
    private SupplierInvoice entity(final Long id) {
        return this.supplierInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier invoice not found with id " + id));
    }

    @Override
    public SupplierInvoiceResponse create(final SupplierInvoiceRequest request) {
        final SupplierInvoice invoice = new SupplierInvoice();
        this.map(invoice, request);
        final SupplierInvoice saved = this.supplierInvoiceRepository.save(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", saved.getId(), "CREATE",
                this.messageService.get("audit.supplierInvoice.created", saved.getInvoiceNumber()));
        return this.supplierInvoiceMapper.toResponse(saved, this.settlementOf(saved, this.paidOn(saved.getId())));
    }

    @Override
    public SupplierInvoiceResponse update(final Long id, final SupplierInvoiceRequest request) {
        final SupplierInvoice invoice = this.entity(id);
        this.map(invoice, request);
        final SupplierInvoice saved = this.supplierInvoiceRepository.save(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", saved.getId(), "UPDATE",
                this.messageService.get("audit.supplierInvoice.updated", saved.getInvoiceNumber()));
        return this.supplierInvoiceMapper.toResponse(saved, this.settlementOf(saved, this.paidOn(saved.getId())));
    }

    @Override
    public void delete(final Long id) {
        final SupplierInvoice invoice = this.entity(id);
        final String invoiceNumber = invoice.getInvoiceNumber();
        this.supplierInvoiceRepository.delete(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", id, "DELETE",
                this.messageService.get("audit.supplierInvoice.deleted", invoiceNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierInvoiceResponse> findByProject(final Long projectId, final Pageable pageable) {
        return this.toResponsePage(this.supplierInvoiceRepository.findByProjectId(projectId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierInvoiceResponse> findBySupplier(final Long supplierId, final Pageable pageable) {
        return this.toResponsePage(this.supplierInvoiceRepository.findBySupplierId(supplierId, pageable));
    }

    @Override
    public SupplierPaymentResponse addPayment(final Long invoiceId, final SupplierPaymentRequest request) {
        final SupplierInvoice invoice = this.entity(invoiceId);
        final BigDecimal alreadyPaid = this.paidOn(invoiceId);
        final BigDecimal gross = this.normalize(invoice.getAmountTtc());

        if (alreadyPaid.add(request.getAmount()).compareTo(gross) > 0) {
            throw new IllegalArgumentException(this.messageService.get("validation.supplierPayment.exceedsInvoice",
                    alreadyPaid.add(request.getAmount()), gross));
        }

        final SupplierPayment payment = new SupplierPayment();
        payment.setInvoice(invoice);
        payment.setPaymentDate(request.getPaymentDate());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod() == null
                ? com.promoteur.app.enums.PaymentMethod.BANK_TRANSFER
                : request.getPaymentMethod());
        payment.setReference(request.getReference());
        payment.setNotes(request.getNotes());
        final SupplierPayment saved = this.supplierPaymentRepository.save(payment);

        this.auditLogService.create("SUPPLIER_INVOICE", invoiceId, "PAYMENT",
                this.messageService.get("audit.supplierPayment.added", saved.getAmount(),
                        invoice.getInvoiceNumber()));
        return this.toPaymentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierPaymentResponse> findPayments(final Long invoiceId) {
        this.entity(invoiceId);
        return this.supplierPaymentRepository.findByInvoiceIdOrderByPaymentDateDesc(invoiceId).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Override
    public void deletePayment(final Long invoiceId, final Long paymentId) {
        final SupplierInvoice invoice = this.entity(invoiceId);
        final SupplierPayment payment = this.supplierPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        this.messageService.get("error.notFound.supplierPayment", String.valueOf(paymentId))));
        if (!payment.getInvoice().getId().equals(invoiceId)) {
            throw new IllegalArgumentException(this.messageService.get("validation.supplierPayment.wrongInvoice"));
        }

        final BigDecimal amount = payment.getAmount();
        this.supplierPaymentRepository.delete(payment);
        this.auditLogService.create("SUPPLIER_INVOICE", invoiceId, "PAYMENT",
                this.messageService.get("audit.supplierPayment.removed", amount, invoice.getInvoiceNumber()));
    }

    @Override
    @Transactional(readOnly = true)
    public PayablesSummaryResponse payablesSummary(final Long projectId) {
        final List<SupplierInvoice> invoices = projectId == null
                ? this.supplierInvoiceRepository.findAll()
                : this.supplierInvoiceRepository.findByProjectId(projectId, Pageable.unpaged()).getContent();

        final Map<Long, BigDecimal> paidByInvoice = this.paidByInvoice(
                invoices.stream().map(SupplierInvoice::getId).toList());

        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        long overdueCount = 0;

        for (final SupplierInvoice invoice : invoices) {
            final InvoiceSettlement settlement = this.settlementOf(invoice,
                    paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO));
            invoiced = invoiced.add(this.normalize(invoice.getAmountTtc()));
            paid = paid.add(settlement.paidAmount());
            due = due.add(settlement.remainingAmount());
            if (settlement.overdue()) {
                overdueCount++;
                overdueAmount = overdueAmount.add(settlement.remainingAmount());
            }
        }

        return new PayablesSummaryResponse(invoices.size(), invoiced, paid, due, overdueCount, overdueAmount);
    }

    /** Maps a whole page, resolving every invoice's payments in one grouped query. */
    private Page<SupplierInvoiceResponse> toResponsePage(final Page<SupplierInvoice> page) {
        final Map<Long, BigDecimal> paidByInvoice = this.paidByInvoice(
                page.getContent().stream().map(SupplierInvoice::getId).toList());
        return page.map(invoice -> this.supplierInvoiceMapper.toResponse(invoice,
                this.settlementOf(invoice, paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO))));
    }

    private Map<Long, BigDecimal> paidByInvoice(final List<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        return this.supplierPaymentRepository.sumByInvoiceIds(invoiceIds).stream()
                .collect(java.util.stream.Collectors.toMap(InvoicePaymentTotal::invoiceId,
                        total -> this.normalize(total.totalAmount())));
    }

    private BigDecimal paidOn(final Long invoiceId) {
        return this.paidByInvoice(List.of(invoiceId)).getOrDefault(invoiceId, BigDecimal.ZERO);
    }

    /**
     * Reads an invoice's state from what has been paid against it. An invoice is late only when
     * it carries a due date, that date has passed, and something is still owed.
     */
    private InvoiceSettlement settlementOf(final SupplierInvoice invoice, final BigDecimal paid) {
        final BigDecimal gross = this.normalize(invoice.getAmountTtc());
        final BigDecimal settled = this.normalize(paid);
        final BigDecimal remaining = gross.subtract(settled).max(BigDecimal.ZERO);

        final SettlementStatus status;
        if (remaining.signum() <= 0) {
            status = SettlementStatus.PAID;
        } else if (settled.signum() > 0) {
            status = SettlementStatus.PARTIALLY_PAID;
        } else {
            status = SettlementStatus.UNPAID;
        }

        final boolean overdue = remaining.signum() > 0
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now());
        final long daysLate = overdue
                ? java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now())
                : 0L;

        return new InvoiceSettlement(settled, remaining, status, overdue, daysLate);
    }

    private SupplierPaymentResponse toPaymentResponse(final SupplierPayment payment) {
        return new SupplierPaymentResponse(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getInvoice().getInvoiceNumber(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getReference(),
                payment.getNotes());
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private void map(final SupplierInvoice invoice, final SupplierInvoiceRequest request) {
        // Le backend est seul maitre du calcul : HT + taux donnent la TVA et le TTC.
        final VatAmounts amounts = this.vatCalculationService.compute(request.getAmountHt(), request.getVatRate());
        this.vatCalculationService.rejectInconsistentDeclaration(amounts, request.getVatAmount(), request.getAmountTtc());

        final Supplier supplier = this.supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id " + request.getSupplierId()));
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));

        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setAmountHt(request.getAmountHt());
        invoice.setVatRate(request.getVatRate());
        invoice.setVatAmount(amounts.vatAmount());
        invoice.setAmountTtc(amounts.amountTtc());
        invoice.setDetail(request.getDetail());
        invoice.setSupplier(supplier);
        invoice.setProject(project);
    }
}
