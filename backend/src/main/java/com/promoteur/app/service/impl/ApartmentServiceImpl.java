package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.ApartmentMapper;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.ApartmentService;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ApartmentServiceImpl implements ApartmentService {

    private final ApartmentRepository apartmentRepository;
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final ApartmentMapper apartmentMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ApartmentResponse> findAll(final Pageable pageable) {
        return this.apartmentRepository.findAll(pageable).map(this.apartmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ApartmentResponse findById(final Long id) {
        return this.apartmentMapper.toResponse(this.entity(id));
    }

    /** Loads the persisted Apartment, for the write paths that need the entity itself. */
    private Apartment entity(final Long id) {
        return this.apartmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Apartment not found with id " + id));
    }

    @Override
    public ApartmentResponse create(final ApartmentRequest request) {
        final Apartment apartment = new Apartment();
        this.map(apartment, request);
        final Apartment saved = this.apartmentRepository.save(apartment);
        this.auditLogService.create("APARTMENT", saved.getId(), "CREATE",
                this.messageService.get("audit.apartment.created", saved.getApartmentNumber()));
        return this.apartmentMapper.toResponse(saved);
    }

    @Override
    public ApartmentResponse update(final Long id, final ApartmentRequest request) {
        final Apartment apartment = this.entity(id);
        this.map(apartment, request);
        final Apartment saved = this.apartmentRepository.save(apartment);
        this.auditLogService.create("APARTMENT", saved.getId(), "UPDATE",
                this.messageService.get("audit.apartment.updated", saved.getApartmentNumber()));
        return this.apartmentMapper.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final Apartment apartment = this.entity(id);
        final String apartmentNumber = apartment.getApartmentNumber();
        this.apartmentRepository.delete(apartment);
        this.auditLogService.create("APARTMENT", id, "DELETE",
                this.messageService.get("audit.apartment.deleted", apartmentNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApartmentResponse> findByProject(final Long projectId, final Pageable pageable) {
        return this.apartmentRepository.findByProjectId(projectId, pageable).map(this.apartmentMapper::toResponse);
    }

    private void map(final Apartment apartment, final ApartmentRequest request) {
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));
        Client acquirer = null;
        if (request.getAcquirerId() != null) {
            acquirer = this.clientRepository.findById(request.getAcquirerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + request.getAcquirerId()));
            if (acquirer.getProject() == null || !project.getId().equals(acquirer.getProject().getId())) {
                throw new ResourceNotFoundException("Client " + acquirer.getId() + " does not belong to project " + project.getId());
            }
        }

        apartment.setApartmentNumber(request.getApartmentNumber());
        apartment.setApartmentType(request.getApartmentType());
        apartment.setTotalSurface(request.getTotalSurface());
        apartment.setGardenSurface(request.getGardenSurface());
        apartment.setParkingCount(request.getParkingCount());
        apartment.setCellarCount(request.getCellarCount());
        apartment.setTotalSalePrice(request.getTotalSalePrice());
        apartment.setDetail(request.getDetail());
        apartment.setProject(project);
        apartment.setAcquirer(acquirer);
    }
}
