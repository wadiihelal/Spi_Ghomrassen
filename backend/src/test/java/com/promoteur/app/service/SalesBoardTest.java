package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SalesBoardBlockResponse;
import com.promoteur.app.dto.response.SalesBoardFloorResponse;
import com.promoteur.app.dto.response.SalesBoardResponse;
import com.promoteur.app.dto.response.SalesBoardUnitResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.enums.SalesStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the sales board (UX-05): a unit is sold because a contract says so, reservation and
 * delivery are decisions the promoter records, and the board totals the stock by block and floor.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_board;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesBoardTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;

    private ProjectResponse project;
    private ClientResponse client;

    @BeforeAll
    void seedProjectAndClient() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("BOARD-PRJ");
        request.setName("Projet plan de vente");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(request);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setFullName("Acquéreur plan de vente");
        clientRequest.setProjectId(this.project.id());
        this.client = this.clientService.create(clientRequest);
    }

    @Test
    @DisplayName("a new apartment is in stock")
    void aNewApartmentIsInStock() {
        assertThat(this.createApartment("Bloc A", 1).salesStatus()).isEqualTo(SalesStatus.AVAILABLE);
    }

    @Test
    @DisplayName("recording a sale contract marks its apartment sold")
    void recordingASaleContractMarksItsApartmentSold() {
        ApartmentResponse apartment = this.createApartment("Bloc A", 1);

        this.createPurchase(apartment, "120000.000");

        assertThat(this.apartmentService.findById(apartment.id()).salesStatus()).isEqualTo(SalesStatus.SOLD);
    }

    @Test
    @DisplayName("an apartment can be held for a buyer before any contract")
    void anApartmentCanBeHeldForABuyer() {
        ApartmentResponse apartment = this.createApartment("Bloc A", 2);

        assertThat(this.apartmentService.changeSalesStatus(apartment.id(), SalesStatus.RESERVED).salesStatus())
                .isEqualTo(SalesStatus.RESERVED);
    }

    @Test
    @DisplayName("an apartment cannot be marked sold without its contract")
    void anApartmentCannotBeMarkedSoldWithoutItsContract() {
        ApartmentResponse apartment = this.createApartment("Bloc A", 2);

        assertThatThrownBy(() -> this.apartmentService.changeSalesStatus(apartment.id(), SalesStatus.SOLD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contrat de vente");
    }

    @Test
    @DisplayName("an apartment under contract cannot go back into stock")
    void anApartmentUnderContractCannotGoBackIntoStock() {
        ApartmentResponse apartment = this.createApartment("Bloc A", 3);
        this.createPurchase(apartment, "150000.000");

        assertThatThrownBy(() -> this.apartmentService.changeSalesStatus(apartment.id(), SalesStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contrat de vente");
    }

    @Test
    @DisplayName("a sold apartment can be marked delivered")
    void aSoldApartmentCanBeMarkedDelivered() {
        ApartmentResponse apartment = this.createApartment("Bloc A", 3);
        this.createPurchase(apartment, "150000.000");

        assertThat(this.apartmentService.changeSalesStatus(apartment.id(), SalesStatus.DELIVERED).salesStatus())
                .isEqualTo(SalesStatus.DELIVERED);
    }

    @Test
    @DisplayName("deleting the contract puts a sold apartment back on its buyer's reservation")
    void deletingTheContractPutsTheApartmentBackToReserved() {
        ApartmentResponse apartment = this.createApartment("Bloc A", 4);
        var purchase = this.createPurchase(apartment, "130000.000");

        this.clientPurchaseService.delete(purchase.id());

        // The apartment still carries its acquirer, so it is held rather than back in stock.
        assertThat(this.apartmentService.findById(apartment.id()).salesStatus()).isEqualTo(SalesStatus.RESERVED);
    }

    @Test
    @DisplayName("the board counts every state of the stock")
    void theBoardCountsEveryStateOfTheStock() {
        ProjectResponse scope = this.createProject("BOARD-COUNT");
        ApartmentResponse inStock = this.createApartment(scope, "Bloc Z", 0, "80000.000");
        ApartmentResponse held = this.createApartment(scope, "Bloc Z", 0, "90000.000");
        ApartmentResponse sold = this.createApartment(scope, "Bloc Z", 1, "100000.000");
        this.apartmentService.changeSalesStatus(held.id(), SalesStatus.RESERVED);
        this.createPurchase(scope, sold, "100000.000");

        SalesBoardResponse board = this.apartmentService.salesBoard(scope.id());

        assertThat(board.unitCount()).isEqualTo(3);
        assertThat(board.availableCount()).isEqualTo(1);
        assertThat(board.reservedCount()).isEqualTo(1);
        assertThat(board.soldCount()).isEqualTo(1);
        assertThat(board.deliveredCount()).isZero();
        assertThat(board.inventoryValue()).isEqualByComparingTo("270000.000");
        assertThat(board.availableValue()).isEqualByComparingTo("80000.000");
        // Everything not in stock is placed, so the two shares rebuild the inventory.
        assertThat(board.placedValue().add(board.availableValue())).isEqualByComparingTo(board.inventoryValue());
        assertThat(board.contractedAmount()).isEqualByComparingTo("100000.000");
        assertThat(inStock.salesStatus()).isEqualTo(SalesStatus.AVAILABLE);
    }

    @Test
    @DisplayName("the board groups units by block and reads floors from the top down")
    void theBoardGroupsUnitsByBlockAndReadsFloorsFromTheTop() {
        ProjectResponse scope = this.createProject("BOARD-LAYOUT");
        this.createApartment(scope, "Bloc A", 0, "70000.000");
        this.createApartment(scope, "Bloc A", 2, "90000.000");
        this.createApartment(scope, "Bloc B", 1, "80000.000");

        SalesBoardResponse board = this.apartmentService.salesBoard(scope.id());

        assertThat(board.blocks()).extracting(SalesBoardBlockResponse::block).containsExactly("Bloc A", "Bloc B");
        assertThat(board.blocks().get(0).floors()).extracting(SalesBoardFloorResponse::floorNumber)
                .containsExactly(2, 0);
        assertThat(board.blocks().get(0).floors()).extracting(SalesBoardFloorResponse::label)
                .containsExactly("2e étage", "RDC");
        assertThat(board.blocks().get(0).unitCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("units recorded without a block are grouped rather than dropped")
    void unitsWithoutABlockAreGroupedRatherThanDropped() {
        ProjectResponse scope = this.createProject("BOARD-NOBLOCK");
        this.createApartment(scope, null, null, "60000.000");

        SalesBoardResponse board = this.apartmentService.salesBoard(scope.id());

        assertThat(board.unitCount()).isEqualTo(1);
        assertThat(board.blocks()).singleElement().satisfies(block -> {
            assertThat(block.block()).isEqualTo("Sans bloc");
            assertThat(block.floors()).singleElement().satisfies(floor -> {
                assertThat(floor.floorNumber()).isNull();
                assertThat(floor.label()).isEqualTo("Étage non renseigné");
            });
        });
    }

    @Test
    @DisplayName("each unit on the board carries what its buyer has paid and still owes")
    void eachUnitCarriesWhatItsBuyerHasPaidAndStillOwes() {
        ProjectResponse scope = this.createProject("BOARD-CASH");
        ClientResponse buyer = this.createClient(scope);
        ApartmentResponse apartment = this.createApartment(scope, "Bloc C", 1, "100000.000");
        this.createPurchase(scope, buyer, apartment, "100000.000", "20000.000");
        this.createAdvance(apartment, "15000.000");

        SalesBoardResponse board = this.apartmentService.salesBoard(scope.id());
        List<SalesBoardUnitResponse> units = board.blocks().get(0).floors().get(0).units();

        assertThat(units).singleElement().satisfies(unit -> {
            assertThat(unit.contractedAmount()).isEqualByComparingTo("100000.000");
            assertThat(unit.collectedAmount()).isEqualByComparingTo("35000.000");
            assertThat(unit.remainingAmount()).isEqualByComparingTo("65000.000");
            assertThat(unit.acquirerName()).isEqualTo(buyer.fullName());
        });
        assertThat(board.collectedAmount()).isEqualByComparingTo("35000.000");
        assertThat(board.remainingAmount()).isEqualByComparingTo("65000.000");
    }

    @Test
    @DisplayName("the board of a project ignores another project's stock")
    void theBoardOfAProjectIgnoresAnotherProjectsStock() {
        ProjectResponse first = this.createProject("BOARD-SCOPE-1");
        ProjectResponse second = this.createProject("BOARD-SCOPE-2");
        this.createApartment(first, "Bloc A", 0, "50000.000");
        this.createApartment(second, "Bloc A", 0, "50000.000");
        this.createApartment(second, "Bloc A", 0, "50000.000");

        assertThat(this.apartmentService.salesBoard(first.id()).unitCount()).isEqualTo(1);
        assertThat(this.apartmentService.salesBoard(second.id()).unitCount()).isEqualTo(2);
    }

    // --- fixtures -------------------------------------------------------------

    private ProjectResponse createProject(String code) {
        ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName("Projet " + code);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient(ProjectResponse scope) {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur " + this.sequence.incrementAndGet());
        request.setProjectId(scope.id());
        return this.clientService.create(request);
    }

    private ApartmentResponse createApartment(String block, Integer floor) {
        return this.createApartment(this.project, block, floor, "120000.000");
    }

    private ApartmentResponse createApartment(ProjectResponse scope, String block, Integer floor, String price) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("B-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal(price));
        request.setBlock(block);
        request.setFloorNumber(floor);
        request.setProjectId(scope.id());
        return this.apartmentService.create(request);
    }

    private com.promoteur.app.dto.response.ClientPurchaseResponse createPurchase(ApartmentResponse apartment,
                                                                                String total) {
        return this.createPurchase(this.project, apartment, total);
    }

    private com.promoteur.app.dto.response.ClientPurchaseResponse createPurchase(ProjectResponse scope,
                                                                                ApartmentResponse apartment,
                                                                                String total) {
        ClientResponse buyer = scope.id().equals(this.project.id()) ? this.client : this.createClient(scope);
        return this.createPurchase(scope, buyer, apartment, total, "0.000");
    }

    private com.promoteur.app.dto.response.ClientPurchaseResponse createPurchase(ProjectResponse scope,
                                                                                ClientResponse buyer,
                                                                                ApartmentResponse apartment,
                                                                                String total, String paid) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-BOARD-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 1, 5));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(new BigDecimal(total));
        request.setPaidAmount(new BigDecimal(paid));
        request.setClientId(buyer.id());
        request.setProjectId(scope.id());
        request.setApartmentId(apartment.id());
        return this.clientPurchaseService.create(request);
    }

    private void createAdvance(ApartmentResponse apartment, String amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 2, 1));
        request.setAmount(new BigDecimal(amount));
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }
}
