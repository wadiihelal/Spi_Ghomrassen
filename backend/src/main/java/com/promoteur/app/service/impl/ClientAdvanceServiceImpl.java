package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ClientAdvanceService;
import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientAdvanceServiceImpl implements ClientAdvanceService {

    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ApartmentRepository apartmentRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;

    @Override
    public Page<ClientAdvance> findAll(final Pageable pageable) {
        return this.clientAdvanceRepository.findAll(pageable);
    }

    @Override
    public ClientAdvance findById(final Long id) {
        return this.clientAdvanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client advance not found with id " + id));
    }

    @Override
    public ClientAdvance create(final ClientAdvanceRequest request) {
        final ClientAdvance advance = new ClientAdvance();
        this.map(advance, request);

        ClientAdvance saved = this.clientAdvanceRepository.save(advance);
        saved = this.finalizeGeneratedReference(saved, request.getReference());

        this.auditLogService.create("ADVANCE", saved.getId(), "CREATE",
                this.messageService.get("audit.advance.created", saved.getReference()));
        return saved;
    }

    @Override
    public ClientAdvance update(final Long id, final ClientAdvanceRequest request) {
        final ClientAdvance advance = this.findById(id);
        this.map(advance, request);
        final ClientAdvance saved = this.clientAdvanceRepository.save(advance);
        this.auditLogService.create("ADVANCE", saved.getId(), "UPDATE",
                this.messageService.get("audit.advance.updated", saved.getReference()));
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final ClientAdvance advance = this.findById(id);
        final String reference = advance.getReference();
        this.clientAdvanceRepository.delete(advance);
        this.auditLogService.create("ADVANCE", id, "DELETE",
                this.messageService.get("audit.advance.deleted", reference));
    }

    @Override
    public Page<ClientAdvance> findByClient(final Long clientId, final Pageable pageable) {
        return this.clientAdvanceRepository.findByClientId(clientId, pageable);
    }

    @Override
    public Page<ClientAdvance> findByProject(final Long projectId, final Pageable pageable) {
        return this.clientAdvanceRepository.findByProjectId(projectId, pageable);
    }

    private void map(final ClientAdvance clientAdvance, final ClientAdvanceRequest request) {
        // Verrou d'ecriture sur la ligne appartement : le controle de plafond ci-dessous et
        // l'enregistrement qui suit forment une seule operation atomique (CONC-01).
        final Apartment apartment = this.apartmentRepository.findByIdForUpdate(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Apartment not found with id " + request.getApartmentId()));
        final Client client = apartment.getAcquirer();
        if (client == null) {
            throw new ResourceNotFoundException("Apartment has no assigned client/acquirer");
        }
        final Project project = apartment.getProject();
        if (project == null) {
            throw new ResourceNotFoundException("Apartment has no assigned project");
        }

        this.validateAdvanceAmount(clientAdvance, apartment, request.getAmount());

        clientAdvance.setReference(this.resolveReference(clientAdvance, request.getReference()));
        clientAdvance.setAdvanceDate(request.getAdvanceDate());
        clientAdvance.setAmount(request.getAmount());
        clientAdvance.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : com.promoteur.app.enums.PaymentMethod.OTHER);
        clientAdvance.setAttachmentName(request.getAttachmentName());
        clientAdvance.setAttachmentUrl(request.getAttachmentUrl());
        clientAdvance.setNotes(request.getNotes());
        clientAdvance.setClient(client);
        clientAdvance.setProject(project);
        clientAdvance.setApartment(apartment);
    }

    /**
     * Caps the advance. With a sale contract the ceiling is the contract total; without one it
     * is the apartment's declared sale price, and an apartment with neither cannot take an
     * advance at all — before CALC-03 it took unlimited ones.
     */
    private void validateAdvanceAmount(final ClientAdvance clientAdvance, final Apartment apartment, final BigDecimal requestedAmount) {
        final ClientPurchase purchase = this.clientPurchaseRepository.findByApartmentId(apartment.getId()).orElse(null);

        final BigDecimal ceiling;
        final BigDecimal directPaidAmount;
        if (purchase != null) {
            ceiling = this.normalize(purchase.getTotalAmount());
            directPaidAmount = this.normalize(purchase.getPaidAmount());
        } else {
            ceiling = this.normalize(apartment.getTotalSalePrice());
            if (ceiling.signum() <= 0) {
                throw new IllegalArgumentException(this.messageService.get(
                        "validation.advance.noContractOrSalePrice", apartment.getApartmentNumber()));
            }
            directPaidAmount = BigDecimal.ZERO;
        }

        // L'acompte en cours de modification est exclu de la somme des "autres" acomptes.
        final BigDecimal otherAdvancesAmount = this.clientAdvanceRepository.findByApartmentId(apartment.getId()).stream()
                .filter(advance -> clientAdvance.getId() == null || !advance.getId().equals(clientAdvance.getId()))
                .map(ClientAdvance::getAmount)
                .map(this::normalize)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final BigDecimal totalCollectedAmount = directPaidAmount
                .add(otherAdvancesAmount)
                .add(this.normalize(requestedAmount));

        if (totalCollectedAmount.compareTo(ceiling) > 0) {
            throw new IllegalArgumentException("Collected amount exceeds declared amount for apartment " + apartment.getApartmentNumber());
        }
    }

    private String resolveReference(final ClientAdvance clientAdvance, final String requestedReference) {
        if (StringUtils.hasText(requestedReference)) {
            return requestedReference.trim();
        }
        if (StringUtils.hasText(clientAdvance.getReference())) {
            return clientAdvance.getReference();
        }
        if (clientAdvance.getId() != null) {
            return this.formatReference(clientAdvance.getId());
        }
        return "ACC-TMP-" + UUID.randomUUID();
    }

    private ClientAdvance finalizeGeneratedReference(final ClientAdvance clientAdvance, final String requestedReference) {
        if (StringUtils.hasText(requestedReference)) {
            return clientAdvance;
        }

        final String finalReference = this.formatReference(clientAdvance.getId());
        if (finalReference.equals(clientAdvance.getReference())) {
            return clientAdvance;
        }

        clientAdvance.setReference(finalReference);
        return this.clientAdvanceRepository.save(clientAdvance);
    }

    private String formatReference(final Long id) {
        return String.format("ACC-%05d", id);
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
