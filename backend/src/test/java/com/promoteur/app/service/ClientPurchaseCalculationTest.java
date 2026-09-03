package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ClientPurchaseResponse;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.enums.PurchasePaymentStatus;
import com.promoteur.app.repository.ClientPurchaseRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the rules that decide how much a client still owes (TEST-01): the payment status, the
 * enrichment arithmetic, and the ceiling on the collected amount.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_purchases;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientPurchaseCalculationTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;

    private ProjectResponse project;
    private ClientResponse client;

    @BeforeAll
    void seedProjectAndClient() {
        this.project = this.createProject();
        this.client = this.createClient();
    }

    @Test
    @DisplayName("a contract with nothing collected is UNPAID")
    void aContractWithNothingCollectedIsUnpaid() {
        ClientPurchaseResponse purchase = this.createPurchase(new BigDecimal("100000.000"), BigDecimal.ZERO);

        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.UNPAID);
        assertThat(purchase.completed()).isFalse();
        assertThat(purchase.completionPercentage()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("a contract collected in part is PARTIALLY_PAID")
    void aContractCollectedInPartIsPartiallyPaid() {
        ClientPurchaseResponse purchase = this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("30000.000"));

        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.PARTIALLY_PAID);
        assertThat(purchase.completed()).isFalse();
    }

    @Test
    @DisplayName("a contract collected to exactly its total is PAID")
    void aContractCollectedToExactlyItsTotalIsPaid() {
        ClientPurchaseResponse purchase = this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("100000.000"));

        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(purchase.completed()).isTrue();
        assertThat(purchase.remainingAmount()).isEqualByComparingTo("0.000");
        assertThat(purchase.completionPercentage()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("a legacy contract collected beyond its total is PAID, never negative, capped at 100%")
    void aLegacyContractCollectedBeyondItsTotalIsPaidAndCapped() {
        ClientPurchaseResponse created = this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("100000.000"));

        // The service refuses to collect more than the total, so an over-collected row can only
        // come from data written before that rule existed. Forced here to prove the read path
        // copes with it.
        ClientPurchase stored = this.clientPurchaseRepository.findById(created.id()).orElseThrow();
        stored.setPaidAmount(new BigDecimal("120000.000"));
        this.clientPurchaseRepository.saveAndFlush(stored);

        ClientPurchaseResponse purchase = this.clientPurchaseService.findById(created.id());

        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(purchase.remainingAmount()).isEqualByComparingTo("0.000");
        assertThat(purchase.completionPercentage()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("the collected amount is the direct payment plus every advance on the apartment")
    void theCollectedAmountIsTheDirectPaymentPlusEveryAdvance() {
        ApartmentResponse apartment = this.createApartment();
        this.createPurchaseFor(apartment, new BigDecimal("100000.000"), new BigDecimal("30000.000"));
        this.createAdvance(apartment, new BigDecimal("15000.000"));
        this.createAdvance(apartment, new BigDecimal("5000.000"));

        ClientPurchaseResponse purchase = this.clientPurchaseService.findById(
                this.clientPurchaseRepository.findByApartmentId(apartment.id()).orElseThrow().getId());

        assertThat(purchase.advanceAmount()).isEqualByComparingTo("20000.000");
        assertThat(purchase.collectedAmount()).isEqualByComparingTo("50000.000");
        assertThat(purchase.remainingAmount()).isEqualByComparingTo("50000.000");
        assertThat(purchase.completionPercentage()).isEqualByComparingTo("50.000");
        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("the completion percentage is correct at scale 3")
    void theCompletionPercentageIsCorrectAtScaleThree() {
        // 1000 collected out of 3000 -> 33.333 %
        ClientPurchaseResponse purchase = this.createPurchase(new BigDecimal("3000.000"), new BigDecimal("1000.000"));

        assertThat(purchase.completionPercentage()).isEqualByComparingTo("33.333");
    }

    @Test
    @DisplayName("a contract of zero completes at 0%, not by dividing by zero")
    void aContractOfZeroCompletesAtZeroPercent() {
        ClientPurchaseResponse purchase = this.createPurchase(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(purchase.completionPercentage()).isEqualByComparingTo("0.000");
        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("a direct payment above the contract total is refused")
    void aDirectPaymentAboveTheContractTotalIsRefused() {
        assertThatThrownBy(() -> this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("100000.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dépasse le montant du contrat");
    }

    @Test
    @DisplayName("a direct payment plus existing advances above the total is refused")
    void aDirectPaymentPlusExistingAdvancesAboveTheTotalIsRefused() {
        ApartmentResponse apartment = this.createApartment();
        this.createAdvance(apartment, new BigDecimal("60000.000"));

        assertThatThrownBy(() -> this.createPurchaseFor(apartment, new BigDecimal("100000.000"),
                new BigDecimal("50000.000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dépasse le montant du contrat");
    }

    @Test
    @DisplayName("a direct payment plus advances landing exactly on the total is accepted")
    void aDirectPaymentPlusAdvancesLandingExactlyOnTheTotalIsAccepted() {
        ApartmentResponse apartment = this.createApartment();
        this.createAdvance(apartment, new BigDecimal("40000.000"));

        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, new BigDecimal("100000.000"),
                new BigDecimal("60000.000"));

        assertThat(purchase.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(purchase.remainingAmount()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("an apartment cannot carry two sale contracts")
    void anApartmentCannotCarryTwoSaleContracts() {
        ApartmentResponse apartment = this.createApartment();
        this.createPurchaseFor(apartment, new BigDecimal("100000.000"), BigDecimal.ZERO);

        assertThatThrownBy(() -> this.createPurchaseFor(apartment, new BigDecimal("90000.000"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fait déjà l'objet d'un contrat de vente");
    }

    private ClientPurchaseResponse createPurchase(BigDecimal totalAmount, BigDecimal paidAmount) {
        return this.createPurchaseFor(this.createApartment(), totalAmount, paidAmount);
    }

    private ClientPurchaseResponse createPurchaseFor(ApartmentResponse apartment, BigDecimal totalAmount, BigDecimal paidAmount) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-CALC-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(totalAmount);
        request.setPaidAmount(paidAmount);
        request.setClientId(this.client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartment.id());
        return this.clientPurchaseService.create(request);
    }

    private void createAdvance(ApartmentResponse apartment, BigDecimal amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(amount);
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }

    private ApartmentResponse createApartment() {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("P-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(this.project.id());
        request.setAcquirerId(this.client.id());
        return this.apartmentService.create(request);
    }

    private ProjectResponse createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("CALC-PRJ");
        request.setName("Projet calculs");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur calculs");
        request.setProjectId(this.project.id());
        return this.clientService.create(request);
    }
}
