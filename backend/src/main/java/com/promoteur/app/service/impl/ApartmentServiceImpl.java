package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ApartmentAdvanceTotal;
import com.promoteur.app.dto.PurchaseTotals;
import com.promoteur.app.dto.ApartmentTotals;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.SalesBoardBlockResponse;
import com.promoteur.app.dto.response.SalesBoardFloorResponse;
import com.promoteur.app.dto.response.SalesBoardResponse;
import com.promoteur.app.dto.response.SalesBoardUnitResponse;
import com.promoteur.app.enums.SalesStatus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ApartmentServiceImpl implements ApartmentService {

    /** Sort key standing in for a missing floor, so it lands under the real ones. */
    private static final int UNKNOWN_FLOOR = Integer.MIN_VALUE;

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

    @Override
    public ApartmentResponse changeSalesStatus(final Long id, final SalesStatus status) {
        final Apartment apartment = this.entity(id);
        final boolean contracted = this.clientPurchaseRepository.findByApartmentId(id).isPresent();

        // The contract, not the operator, decides whether a unit is in stock or sold.
        if (!contracted && (status == SalesStatus.SOLD || status == SalesStatus.DELIVERED)) {
            final String key = status == SalesStatus.SOLD
                    ? "validation.apartment.soldNeedsContract"
                    : "validation.apartment.deliveredNeedsContract";
            throw new IllegalArgumentException(this.messageService.get(key, apartment.getApartmentNumber()));
        }
        if (contracted && (status == SalesStatus.AVAILABLE || status == SalesStatus.RESERVED)) {
            throw new IllegalArgumentException(this.messageService.get(
                    "validation.apartment.contractedCannotReturnToStock", apartment.getApartmentNumber()));
        }

        apartment.setSalesStatus(status);
        final Apartment saved = this.apartmentRepository.save(apartment);
        this.auditLogService.create("APARTMENT", saved.getId(), "UPDATE",
                this.messageService.get("audit.apartment.salesStatusChanged",
                        saved.getApartmentNumber(), status.name()));
        return this.apartmentMapper.toResponse(saved, this.totalsFor(saved.getId()));
    }

    /**
     * The whole stock of a project laid out by block and floor (UX-05).
     *
     * <p>One query brings the units, then two grouped queries resolve every contract and every
     * advance at once: the board would otherwise issue two queries per apartment.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public SalesBoardResponse salesBoard(final Long projectId) {
        final List<Apartment> apartments = projectId == null
                ? this.apartmentRepository.findAllForBoard()
                : this.apartmentRepository.findByProjectIdForBoard(projectId);
        if (apartments.isEmpty()) {
            return ApartmentServiceImpl.emptyBoard();
        }

        final List<Long> ids = apartments.stream().map(Apartment::getId).toList();
        final Map<Long, BigDecimal> advances = this.clientAdvanceRepository.sumAmountByApartmentIds(ids).stream()
                .collect(Collectors.toMap(ApartmentAdvanceTotal::apartmentId, ApartmentAdvanceTotal::totalAmount));
        final Map<Long, ClientPurchase> purchases = this.clientPurchaseRepository.findByApartmentIdIn(ids).stream()
                .collect(Collectors.toMap(purchase -> purchase.getApartment().getId(), purchase -> purchase));

        final Map<String, Map<Integer, List<SalesBoardUnitResponse>>> layout = new LinkedHashMap<>();
        final Map<SalesStatus, Long> counts = new LinkedHashMap<>();
        BigDecimal inventoryValue = BigDecimal.ZERO;
        BigDecimal availableValue = BigDecimal.ZERO;
        BigDecimal contracted = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal remaining = BigDecimal.ZERO;

        for (final Apartment apartment : apartments) {
            final ApartmentTotals totals = this.totals(purchases.get(apartment.getId()),
                    advances.getOrDefault(apartment.getId(), BigDecimal.ZERO));
            final BigDecimal price = ApartmentServiceImpl.zeroIfNull(apartment.getTotalSalePrice());

            inventoryValue = inventoryValue.add(price);
            if (apartment.getSalesStatus() == SalesStatus.AVAILABLE) {
                availableValue = availableValue.add(price);
            }
            contracted = contracted.add(ApartmentServiceImpl.zeroIfNull(totals.totalPurchases()));
            collected = collected.add(ApartmentServiceImpl.zeroIfNull(totals.totalCollected()));
            remaining = remaining.add(ApartmentServiceImpl.zeroIfNull(totals.remainingToCollect()));
            counts.merge(apartment.getSalesStatus(), 1L, Long::sum);

            layout.computeIfAbsent(ApartmentServiceImpl.blockOf(apartment), key -> new LinkedHashMap<>())
                    .computeIfAbsent(ApartmentServiceImpl.floorOf(apartment), key -> new ArrayList<>())
                    .add(new SalesBoardUnitResponse(
                            apartment.getId(),
                            apartment.getApartmentNumber(),
                            apartment.getApartmentType(),
                            apartment.getTotalSurface(),
                            price,
                            apartment.getSalesStatus(),
                            apartment.getAcquirer() == null ? null : apartment.getAcquirer().getFullName(),
                            ApartmentServiceImpl.zeroIfNull(totals.totalPurchases()),
                            ApartmentServiceImpl.zeroIfNull(totals.totalCollected()),
                            ApartmentServiceImpl.zeroIfNull(totals.remainingToCollect())));
        }

        return new SalesBoardResponse(
                apartments.size(),
                counts.getOrDefault(SalesStatus.AVAILABLE, 0L),
                counts.getOrDefault(SalesStatus.RESERVED, 0L),
                counts.getOrDefault(SalesStatus.SOLD, 0L),
                counts.getOrDefault(SalesStatus.DELIVERED, 0L),
                inventoryValue,
                inventoryValue.subtract(availableValue),
                availableValue,
                contracted,
                collected,
                remaining,
                ApartmentServiceImpl.toBlocks(layout));
    }

    /** Floors come out top down, the way a promoter reads a building. */
    private static List<SalesBoardBlockResponse> toBlocks(
            final Map<String, Map<Integer, List<SalesBoardUnitResponse>>> layout) {
        return layout.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(block -> {
                    final List<SalesBoardFloorResponse> floors = block.getValue().entrySet().stream()
                            .sorted(Map.Entry.<Integer, List<SalesBoardUnitResponse>>comparingByKey().reversed())
                            .map(floor -> new SalesBoardFloorResponse(
                                    floor.getKey() == ApartmentServiceImpl.UNKNOWN_FLOOR ? null : floor.getKey(),
                                    ApartmentServiceImpl.floorLabel(floor.getKey()),
                                    floor.getValue().stream()
                                            .sorted(Comparator.comparing(SalesBoardUnitResponse::apartmentNumber))
                                            .toList()))
                            .toList();
                    final int units = floors.stream().mapToInt(floor -> floor.units().size()).sum();
                    final int available = (int) floors.stream()
                            .flatMap(floor -> floor.units().stream())
                            .filter(unit -> unit.salesStatus() == SalesStatus.AVAILABLE)
                            .count();
                    return new SalesBoardBlockResponse(block.getKey(), units, available, floors);
                })
                .toList();
    }

    /** Units recorded before UX-05 have no block; they are grouped rather than hidden. */
    private static String blockOf(final Apartment apartment) {
        final String block = apartment.getBlock();
        return block == null || block.isBlank() ? "Sans bloc" : block;
    }

    private static Integer floorOf(final Apartment apartment) {
        return apartment.getFloorNumber() == null ? ApartmentServiceImpl.UNKNOWN_FLOOR : apartment.getFloorNumber();
    }

    private static String floorLabel(final Integer floor) {
        if (floor == null || floor.equals(ApartmentServiceImpl.UNKNOWN_FLOOR)) {
            return "Étage non renseigné";
        }
        if (floor == 0) {
            return "RDC";
        }
        return floor == 1 ? "1er étage" : floor + "e étage";
    }

    private static BigDecimal zeroIfNull(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static SalesBoardResponse emptyBoard() {
        return new SalesBoardResponse(0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
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
        apartment.setBlock(request.getBlock());
        apartment.setFloorNumber(request.getFloorNumber());
        apartment.setProject(project);
        apartment.setAcquirer(acquirer);
    }
}
