package com.promoteur.app.service.impl;

import com.promoteur.app.dto.SupplierTypeOptionRequest;
import com.promoteur.app.entity.SupplierTypeOption;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.SupplierTypeOptionRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.SupplierTypeOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SupplierTypeOptionServiceImpl implements SupplierTypeOptionService {

    private final SupplierTypeOptionRepository supplierTypeOptionRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;

    @Override
    public Page<SupplierTypeOption> findAll(final Pageable pageable) {
        return this.supplierTypeOptionRepository.findAll(pageable);
    }

    @Override
    public SupplierTypeOption findById(final Long id) {
        return this.supplierTypeOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier type not found with id " + id));
    }

    @Override
    public SupplierTypeOption create(final SupplierTypeOptionRequest request) {
        final SupplierTypeOption type = new SupplierTypeOption();
        this.map(type, request);
        final SupplierTypeOption saved = this.supplierTypeOptionRepository.save(type);
        this.auditLogService.create("SUPPLIER_TYPE", saved.getId(), "CREATE",
                this.messageService.get("audit.supplierType.created", saved.getLabel()));
        return saved;
    }

    @Override
    public SupplierTypeOption update(final Long id, final SupplierTypeOptionRequest request) {
        final SupplierTypeOption type = this.findById(id);
        this.map(type, request);
        final SupplierTypeOption saved = this.supplierTypeOptionRepository.save(type);
        this.auditLogService.create("SUPPLIER_TYPE", saved.getId(), "UPDATE",
                this.messageService.get("audit.supplierType.updated", saved.getLabel()));
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final SupplierTypeOption type = this.findById(id);
        final String label = type.getLabel();
        this.supplierTypeOptionRepository.delete(type);
        this.auditLogService.create("SUPPLIER_TYPE", id, "DELETE",
                this.messageService.get("audit.supplierType.deleted", label));
    }

    private void map(final SupplierTypeOption type, final SupplierTypeOptionRequest request) {
        type.setLabel(request.getLabel());
        type.setActive(request.getActive() == null || request.getActive());
    }
}
