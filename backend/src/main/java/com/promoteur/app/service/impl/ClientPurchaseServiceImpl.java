package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ApartmentAdvanceTotal;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.PurchaseTotals;
import com.promoteur.app.dto.response.ClientPurchaseResponse;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.ClientPurchaseMapper;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ClientPurchaseCalculationService;
import com.promoteur.app.service.ClientPurchaseService;
import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientPurchaseServiceImpl implements ClientPurchaseService {

    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ApartmentRepository apartmentRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final ClientPurchaseCalculationService clientPurchaseCalculationService;
    private final ClientPurchaseMapper clientPurchaseMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ClientPurchaseResponse> findAll(final Pageable pageable) {
        return this.toResponsePage(this.clientPurchaseRepository.findAll(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ClientPurchaseResponse findById(final Long id) {
        return this.toResponse(this.entity(id));
    }

    /** Loads the persisted contract, for the write paths that need the entity itself. */
    private ClientPurchase entity(final Long id) {
        return this.clientPurchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        this.messageService.get("error.notFound.purchase", String.valueOf(id))));
    }

    @Override
    public ClientPurchaseResponse create(final ClientPurchaseRequest request) {
        final ClientPurchase purchase = new ClientPurchase();
        this.map(purchase, request);
        final ClientPurchase saved = this.clientPurchaseRepository.save(purchase);
        this.auditLogService.create("PURCHASE", saved.getId(), "CREATE",
                this.messageService.get("audit.purchase.created", saved.getReference(), saved.getApartment().getApartmentNumber()));
        return this.toResponse(saved);
    }

    @Override
    public ClientPurchaseResponse update(final Long id, final ClientPurchaseRequest request) {
        final ClientPurchase purchase = this.entity(id);
        this.map(purchase, request);
        final ClientPurchase saved = this.clientPurchaseRepository.save(purchase);
        this.auditLogService.create("PURCHASE", saved.getId(), "UPDATE",
                this.messageService.get("audit.purchase.updated", saved.getReference(), saved.getApartment().getApartmentNumber()));
        return this.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final ClientPurchase purchase = this.entity(id);
        final String reference = purchase.getReference();
        this.clientPurchaseRepository.delete(purchase);
        this.auditLogService.create("PURCHASE", id, "DELETE",
                this.messageService.get("audit.purchase.deleted", reference));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientPurchaseResponse> findByClient(final Long clientId, final Pageable pageable) {
        return this.toResponsePage(this.clientPurchaseRepository.findByClientId(clientId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientPurchaseResponse> findByProject(final Long projectId, final Pageable pageable) {
        return this.toResponsePage(this.clientPurchaseRepository.findByProjectId(projectId, pageable));
    }

    private void map(final ClientPurchase purchase, final ClientPurchaseRequest request) {
        final Client client = this.clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException(this.messageService.get(
                        "error.notFound.client", String.valueOf(request.getClientId()))));
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(this.messageService.get(
                        "error.notFound.project", String.valueOf(request.getProjectId()))));
        // Verrou d'ecriture sur la ligne appartement : le controle d'unicite du contrat et le
        // controle de plafond ci-dessous ne peuvent plus etre doubles (CONC-01).
        final Apartment apartment = this.apartmentRepository.findByIdForUpdate(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(this.messageService.get(
                        "error.notFound.apartment", String.valueOf(request.getApartmentId()))));
        final BigDecimal paidAmount = this.normalize(request.getPaidAmount());

        if (client.getProject() == null || !project.getId().equals(client.getProject().getId())) {
            throw new ResourceNotFoundException(this.messageService.get("error.client.notInProject",
                    client.getFullName(), project.getName()));
        }
        if (apartment.getProject() == null || !project.getId().equals(apartment.getProject().getId())) {
            throw new ResourceNotFoundException(this.messageService.get("error.apartment.notInProject",
                    apartment.getApartmentNumber(), project.getName()));
        }
        if (apartment.getAcquirer() != null && !client.getId().equals(apartment.getAcquirer().getId())) {
            throw new ResourceNotFoundException(this.messageService.get(
                    "error.apartment.assignedToAnotherClient", apartment.getApartmentNumber()));
        }

        this.validateUniqueApartmentPurchase(purchase.getId(), apartment.getId(), apartment.getApartmentNumber());
        this.validateCollectedAmount(request.getTotalAmount(), paidAmount, this.sumAdvanceAmount(apartment.getId()), apartment.getApartmentNumber());

        apartment.setAcquirer(client);

        purchase.setReference(request.getReference());
        purchase.setPurchaseDate(request.getPurchaseDate());
        purchase.setContractDate(request.getContractDate());
        purchase.setAssetDescription(request.getAssetDescription());
        purchase.setTotalAmount(request.getTotalAmount());
        purchase.setPaidAmount(paidAmount);
        purchase.setAttachmentName(request.getAttachmentName());
        purchase.setAttachmentUrl(request.getAttachmentUrl());
        purchase.setNotes(request.getNotes());
        purchase.setClient(client);
        purchase.setProject(project);
        purchase.setApartment(apartment);
    }

    private void validateUniqueApartmentPurchase(final Long currentPurchaseId, final Long apartmentId,
                                                final String apartmentNumber) {
        final ClientPurchase existingPurchase = this.clientPurchaseRepository.findByApartmentId(apartmentId).orElse(null);
        if (existingPurchase != null && (currentPurchaseId == null || !existingPurchase.getId().equals(currentPurchaseId))) {
            throw new IllegalArgumentException(
                    this.messageService.get("validation.purchase.apartmentAlreadySold", apartmentNumber));
        }
    }

    /**
     * Maps a whole page, resolving every advance total in a single grouped query instead of one
     * query per row (PERF-03).
     */
    private Page<ClientPurchaseResponse> toResponsePage(final Page<ClientPurchase> page) {
        final List<Long> apartmentIds = page.getContent().stream()
                .map(purchase -> purchase.getApartment() == null ? null : purchase.getApartment().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        final Map<Long, BigDecimal> advancesByApartment = apartmentIds.isEmpty()
                ? Map.of()
                : this.clientAdvanceRepository.sumAmountByApartmentIds(apartmentIds).stream()
                        .collect(Collectors.toMap(ApartmentAdvanceTotal::apartmentId,
                                ApartmentAdvanceTotal::totalAmount));

        return page.map(purchase -> this.toResponse(purchase, this.advanceAmountFor(purchase, advancesByApartment)));
    }

    private BigDecimal advanceAmountFor(final ClientPurchase purchase, final Map<Long, BigDecimal> advances) {
        final Long apartmentId = purchase.getApartment() == null ? null : purchase.getApartment().getId();
        return apartmentId == null ? BigDecimal.ZERO : advances.getOrDefault(apartmentId, BigDecimal.ZERO);
    }

    /** Single-row mapping: the advance total is fetched for that apartment alone. */
    private ClientPurchaseResponse toResponse(final ClientPurchase purchase) {
        final Long apartmentId = purchase.getApartment() == null ? null : purchase.getApartment().getId();
        return this.toResponse(purchase,
                apartmentId == null ? BigDecimal.ZERO : this.sumAdvanceAmount(apartmentId));
    }

    /**
     * Read only: nothing is written back to the entity, so a GET no longer flushes an UPDATE
     * (ARCH-01, ARCH-03).
     */
    private ClientPurchaseResponse toResponse(final ClientPurchase purchase, final BigDecimal advanceAmount) {
        final PurchaseTotals totals = this.clientPurchaseCalculationService.totals(
                purchase.getTotalAmount(), purchase.getPaidAmount(), advanceAmount);
        return this.clientPurchaseMapper.toResponse(purchase, totals);
    }

    private BigDecimal sumAdvanceAmount(final Long apartmentId) {
        final List<ClientAdvance> advances = this.clientAdvanceRepository.findByApartmentId(apartmentId);
        return advances.stream()
                .map(ClientAdvance::getAmount)
                .map(this::normalize)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateCollectedAmount(final BigDecimal totalAmount, final BigDecimal paidAmount, final BigDecimal advanceAmount, final String apartmentNumber) {
        final BigDecimal collectedAmount = paidAmount.add(advanceAmount);
        if (collectedAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException(this.messageService.get("validation.purchase.exceedsTotal",
                    apartmentNumber, collectedAmount, totalAmount));
        }
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
