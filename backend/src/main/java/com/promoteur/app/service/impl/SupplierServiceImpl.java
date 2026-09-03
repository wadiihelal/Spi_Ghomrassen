package com.promoteur.app.service.impl;

import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.entity.Supplier;
import com.promoteur.app.entity.SupplierTypeOption;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.repository.SupplierTypeOptionRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierTypeOptionRepository supplierTypeOptionRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;

    @Override
    public Page<Supplier> findAll(final Pageable pageable) {
        return this.supplierRepository.findAll(pageable);
    }

    @Override
    public Supplier findById(final Long id) {
        return this.supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id " + id));
    }

    @Override
    public Supplier create(final SupplierRequest request) {
        final Supplier supplier = new Supplier();
        this.map(supplier, request);
        final Supplier saved = this.supplierRepository.save(supplier);
        this.auditLogService.create("SUPPLIER", saved.getId(), "CREATE",
                this.messageService.get("audit.supplier.created", saved.getName()));
        return saved;
    }

    @Override
    public Supplier update(final Long id, final SupplierRequest request) {
        final Supplier supplier = this.findById(id);
        this.map(supplier, request);
        final Supplier saved = this.supplierRepository.save(supplier);
        this.auditLogService.create("SUPPLIER", saved.getId(), "UPDATE",
                this.messageService.get("audit.supplier.updated", saved.getName()));
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final Supplier supplier = this.findById(id);
        final String name = supplier.getName();
        this.supplierRepository.delete(supplier);
        this.auditLogService.create("SUPPLIER", id, "DELETE",
                this.messageService.get("audit.supplier.deleted", name));
    }

    private void map(final Supplier supplier, final SupplierRequest request) {
        SupplierTypeOption type = null;
        if (request.getTypeId() != null) {
            type = this.supplierTypeOptionRepository.findById(request.getTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier type not found with id " + request.getTypeId()));
        }
        supplier.setName(request.getName());
        supplier.setFiscalId(request.getFiscalId());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setDefaultVatRate(request.getDefaultVatRate());
        supplier.setType(type);
        supplier.setActive(request.getActive() == null || request.getActive());
    }
}
