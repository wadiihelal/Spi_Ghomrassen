package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ApartmentAdvanceTotal;
import com.promoteur.app.dto.PurchaseTotals;
import com.promoteur.app.dto.ApartmentTotals;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.ApartmentMapper;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.specification.ApartmentSpecifications;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.ApartmentService;
import com.promoteur.app.service.ClientPurchaseCalculationService;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseCalculationService clientPurchaseCalculationService;

    @Override
    @Transactional(readOnly = true)
    public Page<ApartmentResponse> findAll(final ListFilter filter, final Pageable pageable) {
        return this.toResponsePage(
                this.apartmentRepository.findAll(ApartmentSpecifications.matching(filter), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ApartmentResponse findById(final Long id) {
        final Apartment apartment = this.entity(id);
        return this.apartmentMapper.toResponse(apartment, this.totalsFor(apartment.getId()));
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
        return this.apartmentMapper.toResponse(saved, this.totalsFor(saved.getId()));
    }

    @Override
    public ApartmentResponse update(final Long id, final ApartmentRequest request) {
        final Apartment apartment = this.entity(id);
        this.map(apartment, request);
        final Apartment saved = this.apartmentRepository.save(apartment);
        this.auditLogService.create("APARTMENT", saved.getId(), "UPDATE",
                this.messageService.get("audit.apartment.updated", saved.getApartmentNumber()));
        return this.apartmentMapper.toResponse(saved, this.totalsFor(saved.getId()));
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
        return this.toResponsePage(this.apartmentRepository.findByProjectId(projectId, pageable));
    }

    /**
     * Maps a whole page, resolving every apartment's contract and advance totals in two queries
     * rather than one pair per row (PERF-02).
     */
    private Page<ApartmentResponse> toResponsePage(final Page<Apartment> page) {
        final List<Long> ids = page.getContent().stream().map(Apartment::getId).toList();
        if (ids.isEmpty()) {
            return page.map(apartment -> this.apartmentMapper.toResponse(apartment, ApartmentTotals.empty()));
        }

        final Map<Long, BigDecimal> advancesByApartment = this.clientAdvanceRepository
                .sumAmountByApartmentIds(ids).stream()
                .collect(Collectors.toMap(ApartmentAdvanceTotal::apartmentId, ApartmentAdvanceTotal::totalAmount));
        final Map<Long, ClientPurchase> purchasesByApartment = this.clientPurchaseRepository
                .findByApartmentIdIn(ids).stream()
                .collect(Collectors.toMap(purchase -> purchase.getApartment().getId(), purchase -> purchase));

        return page.map(apartment -> this.apartmentMapper.toResponse(apartment,
                this.totals(purchasesByApartment.get(apartment.getId()),
                        advancesByApartment.getOrDefault(apartment.getId(), BigDecimal.ZERO))));
    }

    private ApartmentTotals totalsFor(final Long apartmentId) {
        final BigDecimal advances = this.clientAdvanceRepository.sumAmountByApartmentIds(List.of(apartmentId))
                .stream()
                .map(ApartmentAdvanceTotal::totalAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        return this.totals(this.clientPurchaseRepository.findByApartmentId(apartmentId).orElse(null), advances);
    }

    /** Without a contract nothing is owed yet, so only the advances collected are reported. */
    private ApartmentTotals totals(final ClientPurchase purchase, final BigDecimal advances) {
        if (purchase == null) {
            return new ApartmentTotals(BigDecimal.ZERO, advances, advances, BigDecimal.ZERO);
        }
        final PurchaseTotals totals = this.clientPurchaseCalculationService.totals(
                purchase.getTotalAmount(), purchase.getPaidAmount(), advances);
        return new ApartmentTotals(
                purchase.getTotalAmount(),
                totals.advanceAmount(),
                totals.collectedAmount(),
                totals.remainingAmount());
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
