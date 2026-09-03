package com.promoteur.app.service.impl;

import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.VatAmounts;
import com.promoteur.app.entity.Project;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.entity.SupplierInvoice;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierInvoiceRepository;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.VatCalculationService;
import com.promoteur.app.service.SupplierInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@RequiredArgsConstructor
public class SupplierInvoiceServiceImpl implements SupplierInvoiceService {

    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final VatCalculationService vatCalculationService;

    @Override
    public Page<SupplierInvoice> findAll(final Pageable pageable) {
        return this.supplierInvoiceRepository.findAll(pageable);
    }

    @Override
    public SupplierInvoice findById(final Long id) {
        return this.supplierInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier invoice not found with id " + id));
    }

    @Override
    public SupplierInvoice create(final SupplierInvoiceRequest request) {
        final SupplierInvoice invoice = new SupplierInvoice();
        this.map(invoice, request);
        final SupplierInvoice saved = this.supplierInvoiceRepository.save(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", saved.getId(), "CREATE",
                this.messageService.get("audit.supplierInvoice.created", saved.getInvoiceNumber()));
        return saved;
    }

    @Override
    public SupplierInvoice update(final Long id, final SupplierInvoiceRequest request) {
        final SupplierInvoice invoice = this.findById(id);
        this.map(invoice, request);
        final SupplierInvoice saved = this.supplierInvoiceRepository.save(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", saved.getId(), "UPDATE",
                this.messageService.get("audit.supplierInvoice.updated", saved.getInvoiceNumber()));
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final SupplierInvoice invoice = this.findById(id);
        final String invoiceNumber = invoice.getInvoiceNumber();
        this.supplierInvoiceRepository.delete(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", id, "DELETE",
                this.messageService.get("audit.supplierInvoice.deleted", invoiceNumber));
    }

    @Override
    public Page<SupplierInvoice> findByProject(final Long projectId, final Pageable pageable) {
        return this.supplierInvoiceRepository.findByProjectId(projectId, pageable);
    }

    @Override
    public Page<SupplierInvoice> findBySupplier(final Long supplierId, final Pageable pageable) {
        return this.supplierInvoiceRepository.findBySupplierId(supplierId, pageable);
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
        invoice.setAmountHt(request.getAmountHt());
        invoice.setVatRate(request.getVatRate());
        invoice.setVatAmount(amounts.vatAmount());
        invoice.setAmountTtc(amounts.amountTtc());
        invoice.setAttachmentName(request.getAttachmentName());
        invoice.setAttachmentUrl(request.getAttachmentUrl());
        invoice.setDetail(request.getDetail());
        invoice.setSupplier(supplier);
        invoice.setProject(project);
    }
}
