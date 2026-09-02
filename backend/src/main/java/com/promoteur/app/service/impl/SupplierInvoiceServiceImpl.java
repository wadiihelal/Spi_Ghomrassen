package com.promoteur.app.service.impl;

import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.entity.Project;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.entity.SupplierInvoice;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierInvoiceRepository;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.SupplierInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class SupplierInvoiceServiceImpl implements SupplierInvoiceService {

    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

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
        this.auditLogService.create("SUPPLIER_INVOICE", saved.getId(), "CREATE", "Facture fournisseur " + saved.getInvoiceNumber() + " creee.");
        return saved;
    }

    @Override
    public SupplierInvoice update(final Long id, final SupplierInvoiceRequest request) {
        final SupplierInvoice invoice = this.findById(id);
        this.map(invoice, request);
        final SupplierInvoice saved = this.supplierInvoiceRepository.save(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", saved.getId(), "UPDATE", "Facture fournisseur " + saved.getInvoiceNumber() + " modifiee.");
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final SupplierInvoice invoice = this.findById(id);
        this.supplierInvoiceRepository.delete(invoice);
        this.auditLogService.create("SUPPLIER_INVOICE", id, "DELETE", "Facture fournisseur " + invoice.getInvoiceNumber() + " supprimee.");
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
        this.validateAmounts(request);

        final Supplier supplier = this.supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id " + request.getSupplierId()));
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));

        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setAmountHt(request.getAmountHt());
        invoice.setVatAmount(request.getVatAmount());
        invoice.setAmountTtc(request.getAmountTtc());
        invoice.setWithholdingAmount(request.getWithholdingAmount());
        invoice.setNetToPay(request.getNetToPay());
        invoice.setAttachmentName(request.getAttachmentName());
        invoice.setAttachmentUrl(request.getAttachmentUrl());
        invoice.setDetail(request.getDetail());
        invoice.setSupplier(supplier);
        invoice.setProject(project);
    }

    private void validateAmounts(final SupplierInvoiceRequest request) {
        final BigDecimal expectedAmountTtc = request.getAmountHt().add(request.getVatAmount());
        if (expectedAmountTtc.compareTo(request.getAmountTtc()) != 0) {
            throw new IllegalArgumentException("amountTtc must be equal to amountHt + vatAmount");
        }

        final BigDecimal expectedNetToPay = request.getAmountTtc().subtract(request.getWithholdingAmount());
        if (expectedNetToPay.compareTo(request.getNetToPay()) != 0) {
            throw new IllegalArgumentException("netToPay must be equal to amountTtc - withholdingAmount");
        }
    }
}
