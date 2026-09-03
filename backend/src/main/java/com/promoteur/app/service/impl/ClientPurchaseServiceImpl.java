package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.entity.Project;
import com.promoteur.app.enums.PurchasePaymentStatus;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ClientPurchaseService;
import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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

    @Override
    public Page<ClientPurchase> findAll(final Pageable pageable) {
        return this.clientPurchaseRepository.findAll(pageable).map(this::enrichPurchase);
    }

    @Override
    public ClientPurchase findById(final Long id) {
        final ClientPurchase purchase = this.clientPurchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client purchase not found with id " + id));
        return this.enrichPurchase(purchase);
    }

    @Override
    public ClientPurchase create(final ClientPurchaseRequest request) {
        final ClientPurchase purchase = new ClientPurchase();
        this.map(purchase, request);
        final ClientPurchase saved = this.clientPurchaseRepository.save(purchase);
        final ClientPurchase enrichedPurchase = this.enrichPurchase(saved);
        this.auditLogService.create("PURCHASE", saved.getId(), "CREATE",
                this.messageService.get("audit.purchase.created", saved.getReference(), saved.getApartment().getApartmentNumber()));
        return enrichedPurchase;
    }

    @Override
    public ClientPurchase update(final Long id, final ClientPurchaseRequest request) {
        final ClientPurchase purchase = this.findById(id);
        this.map(purchase, request);
        final ClientPurchase saved = this.clientPurchaseRepository.save(purchase);
        final ClientPurchase enrichedPurchase = this.enrichPurchase(saved);
        this.auditLogService.create("PURCHASE", saved.getId(), "UPDATE",
                this.messageService.get("audit.purchase.updated", saved.getReference(), saved.getApartment().getApartmentNumber()));
        return enrichedPurchase;
    }

    @Override
    public void delete(final Long id) {
        final ClientPurchase purchase = this.findById(id);
        final String reference = purchase.getReference();
        this.clientPurchaseRepository.delete(purchase);
        this.auditLogService.create("PURCHASE", id, "DELETE",
                this.messageService.get("audit.purchase.deleted", reference));
    }

    @Override
    public Page<ClientPurchase> findByClient(final Long clientId, final Pageable pageable) {
        return this.clientPurchaseRepository.findByClientId(clientId, pageable).map(this::enrichPurchase);
    }

    @Override
    public Page<ClientPurchase> findByProject(final Long projectId, final Pageable pageable) {
        return this.clientPurchaseRepository.findByProjectId(projectId, pageable).map(this::enrichPurchase);
    }

    private void map(final ClientPurchase purchase, final ClientPurchaseRequest request) {
        final Client client = this.clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + request.getClientId()));
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));
        // Verrou d'ecriture sur la ligne appartement : le controle d'unicite du contrat et le
        // controle de plafond ci-dessous ne peuvent plus etre doubles (CONC-01).
        final Apartment apartment = this.apartmentRepository.findByIdForUpdate(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Apartment not found with id " + request.getApartmentId()));
        final BigDecimal paidAmount = this.normalize(request.getPaidAmount());

        if (client.getProject() == null || !project.getId().equals(client.getProject().getId())) {
            throw new ResourceNotFoundException("Client " + client.getId() + " does not belong to project " + project.getId());
        }
        if (apartment.getProject() == null || !project.getId().equals(apartment.getProject().getId())) {
            throw new ResourceNotFoundException("Apartment " + apartment.getId() + " does not belong to project " + project.getId());
        }
        if (apartment.getAcquirer() != null && !client.getId().equals(apartment.getAcquirer().getId())) {
            throw new ResourceNotFoundException("Apartment " + apartment.getApartmentNumber() + " is already assigned to another client");
        }

        this.validateUniqueApartmentPurchase(purchase.getId(), apartment.getId());
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

    private void validateUniqueApartmentPurchase(final Long currentPurchaseId, final Long apartmentId) {
        final ClientPurchase existingPurchase = this.clientPurchaseRepository.findByApartmentId(apartmentId).orElse(null);
        if (existingPurchase != null && (currentPurchaseId == null || !existingPurchase.getId().equals(currentPurchaseId))) {
            throw new IllegalArgumentException("This apartment already has a client purchase");
        }
    }

    private ClientPurchase enrichPurchase(final ClientPurchase purchase) {
        final Long apartmentId = purchase.getApartment() != null ? purchase.getApartment().getId() : null;
        final BigDecimal advanceAmount = apartmentId == null ? BigDecimal.ZERO : this.sumAdvanceAmount(apartmentId);
        final BigDecimal directPaidAmount = this.normalize(purchase.getPaidAmount());
        final BigDecimal totalAmount = this.normalize(purchase.getTotalAmount());
        final BigDecimal collectedAmount = directPaidAmount.add(advanceAmount);
        final BigDecimal remainingAmount = totalAmount.subtract(collectedAmount).max(BigDecimal.ZERO);
        final BigDecimal completionPercentage = totalAmount.signum() <= 0
                ? BigDecimal.ZERO
                : collectedAmount.multiply(BigDecimal.valueOf(100))
                  .divide(totalAmount, 3, RoundingMode.HALF_UP)
                  .min(BigDecimal.valueOf(100));
        final PurchasePaymentStatus paymentStatus = this.resolvePaymentStatus(collectedAmount, totalAmount);

        purchase.setPaidAmount(directPaidAmount);
        purchase.setAdvanceAmount(advanceAmount);
        purchase.setCollectedAmount(collectedAmount);
        purchase.setRemainingAmount(remainingAmount);
        purchase.setCompletionPercentage(completionPercentage);
        purchase.setPaymentStatus(paymentStatus);
        purchase.setCompleted(paymentStatus == PurchasePaymentStatus.PAID);
        return purchase;
    }

    private PurchasePaymentStatus resolvePaymentStatus(final BigDecimal collectedAmount, final BigDecimal totalAmount) {
        if (collectedAmount.signum() <= 0) {
            return PurchasePaymentStatus.UNPAID;
        }
        if (collectedAmount.compareTo(totalAmount) >= 0) {
            return PurchasePaymentStatus.PAID;
        }
        return PurchasePaymentStatus.PARTIALLY_PAID;
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
            throw new IllegalArgumentException("Collected amount exceeds declared amount for apartment " + apartmentNumber);
        }
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
