package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.response.ClientAdvanceResponse;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.ClientAdvanceMapper;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.specification.ClientAdvanceSpecifications;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ClientAdvanceService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.ReferenceGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientAdvanceServiceImpl implements ClientAdvanceService {

    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ApartmentRepository apartmentRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final ClientAdvanceMapper clientAdvanceMapper;
    private final ReferenceGeneratorService referenceGeneratorService;

    @Override
    @Transactional(readOnly = true)
    public Page<ClientAdvanceResponse> findAll(final ListFilter filter, final Pageable pageable) {
        return this.clientAdvanceRepository.findAll(ClientAdvanceSpecifications.matching(filter), pageable)
                .map(this.clientAdvanceMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientAdvanceResponse findById(final Long id) {
        return this.clientAdvanceMapper.toResponse(this.entity(id));
    }

    /** Loads the persisted ClientAdvance, for the write paths that need the entity itself. */
    private ClientAdvance entity(final Long id) {
        return this.clientAdvanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        this.messageService.get("error.notFound.advance", String.valueOf(id))));
    }

    @Override
    public ClientAdvanceResponse create(final ClientAdvanceRequest request) {
        final ClientAdvance advance = new ClientAdvance();
        this.map(advance, request);

        final ClientAdvance saved = this.clientAdvanceRepository.save(advance);

        this.auditLogService.create("ADVANCE", saved.getId(), "CREATE",
                this.messageService.get("audit.advance.created", saved.getReference()));
        return this.clientAdvanceMapper.toResponse(saved);
    }

    @Override
    public ClientAdvanceResponse update(final Long id, final ClientAdvanceRequest request) {
        final ClientAdvance advance = this.entity(id);
        this.map(advance, request);
        final ClientAdvance saved = this.clientAdvanceRepository.save(advance);
        this.auditLogService.create("ADVANCE", saved.getId(), "UPDATE",
                this.messageService.get("audit.advance.updated", saved.getReference()));
        return this.clientAdvanceMapper.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final ClientAdvance advance = this.entity(id);
        final String reference = advance.getReference();
        this.clientAdvanceRepository.delete(advance);
        this.auditLogService.create("ADVANCE", id, "DELETE",
                this.messageService.get("audit.advance.deleted", reference));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientAdvanceResponse> findByClient(final Long clientId, final Pageable pageable) {
        return this.clientAdvanceRepository.findByClientId(clientId, pageable).map(this.clientAdvanceMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientAdvanceResponse> findByProject(final Long projectId, final Pageable pageable) {
        return this.clientAdvanceRepository.findByProjectId(projectId, pageable).map(this.clientAdvanceMapper::toResponse);
    }

    private void map(final ClientAdvance clientAdvance, final ClientAdvanceRequest request) {
        // Verrou d'ecriture sur la ligne appartement : le controle de plafond ci-dessous et
        // l'enregistrement qui suit forment une seule operation atomique (CONC-01).
        final Apartment apartment = this.apartmentRepository.findByIdForUpdate(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(this.messageService.get(
                        "error.notFound.apartment", String.valueOf(request.getApartmentId()))));
        final Client client = apartment.getAcquirer();
        if (client == null) {
            throw new ResourceNotFoundException(
                    this.messageService.get("error.apartment.noAcquirer", apartment.getApartmentNumber()));
        }
        final Project project = apartment.getProject();
        if (project == null) {
            throw new ResourceNotFoundException(
                    this.messageService.get("error.apartment.noProject", apartment.getApartmentNumber()));
        }

        this.validateAdvanceAmount(clientAdvance, apartment, request.getAmount());

        clientAdvance.setReference(this.resolveReference(clientAdvance, request));
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
            throw new IllegalArgumentException(this.messageService.get("validation.advance.exceedsCeiling",
                    apartment.getApartmentNumber(), totalCollectedAmount, ceiling));
        }
    }

    /**
     * Resolves the reference before the first save (DATA-03): the caller's own reference when
     * given, the existing one on an update, otherwise a freshly allocated sequential number.
     */
    private String resolveReference(final ClientAdvance clientAdvance, final ClientAdvanceRequest request) {
        if (StringUtils.hasText(request.getReference())) {
            return request.getReference().trim();
        }
        if (StringUtils.hasText(clientAdvance.getReference())) {
            return clientAdvance.getReference();
        }
        return this.referenceGeneratorService.nextAdvanceReference(request.getAdvanceDate());
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
