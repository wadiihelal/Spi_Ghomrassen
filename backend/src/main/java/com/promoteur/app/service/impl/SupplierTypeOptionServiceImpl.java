package com.promoteur.app.service.impl;

import com.promoteur.app.dto.SupplierTypeOptionRequest;
import com.promoteur.app.dto.response.SupplierTypeOptionResponse;
import com.promoteur.app.entity.SupplierTypeOption;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.SupplierTypeOptionMapper;
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
    private final SupplierTypeOptionMapper supplierTypeOptionMapper;

    @Override
    public Page<SupplierTypeOptionResponse> findAll(final Pageable pageable) {
        return this.supplierTypeOptionRepository.findAll(pageable).map(this.supplierTypeOptionMapper::toResponse);
    }

    @Override
    public SupplierTypeOptionResponse findById(final Long id) {
        return this.supplierTypeOptionMapper.toResponse(this.entity(id));
    }

    /** Loads the persisted SupplierTypeOption, for the write paths that need the entity itself. */
    private SupplierTypeOption entity(final Long id) {
        return this.supplierTypeOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier type not found with id " + id));
    }

    @Override
    public SupplierTypeOptionResponse create(final SupplierTypeOptionRequest request) {
        final SupplierTypeOption type = new SupplierTypeOption();
        this.map(type, request);
        final SupplierTypeOption saved = this.supplierTypeOptionRepository.save(type);
        this.auditLogService.create("SUPPLIER_TYPE", saved.getId(), "CREATE",
                this.messageService.get("audit.supplierType.created", saved.getLabel()));
        return this.supplierTypeOptionMapper.toResponse(saved);
    }

    @Override
    public SupplierTypeOptionResponse update(final Long id, final SupplierTypeOptionRequest request) {
        final SupplierTypeOption type = this.entity(id);
        this.map(type, request);
        final SupplierTypeOption saved = this.supplierTypeOptionRepository.save(type);
        this.auditLogService.create("SUPPLIER_TYPE", saved.getId(), "UPDATE",
                this.messageService.get("audit.supplierType.updated", saved.getLabel()));
        return this.supplierTypeOptionMapper.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final SupplierTypeOption type = this.entity(id);
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
