package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.entity.Project;
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

    private Project project;
    private Client client;

    @BeforeAll
    void seedProjectAndClient() {
        this.project = this.createProject();
        this.client = this.createClient();
    }

    @Test
    @DisplayName("a contract with nothing collected is UNPAID")
    void aContractWithNothingCollectedIsUnpaid() {
        ClientPurchase purchase = this.createPurchase(new BigDecimal("100000.000"), BigDecimal.ZERO);

        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.UNPAID);
        assertThat(purchase.getCompleted()).isFalse();
        assertThat(purchase.getCompletionPercentage()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("a contract collected in part is PARTIALLY_PAID")
    void aContractCollectedInPartIsPartiallyPaid() {
        ClientPurchase purchase = this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("30000.000"));

        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.PARTIALLY_PAID);
        assertThat(purchase.getCompleted()).isFalse();
    }

    @Test
    @DisplayName("a contract collected to exactly its total is PAID")
    void aContractCollectedToExactlyItsTotalIsPaid() {
        ClientPurchase purchase = this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("100000.000"));

        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(purchase.getCompleted()).isTrue();
        assertThat(purchase.getRemainingAmount()).isEqualByComparingTo("0.000");
        assertThat(purchase.getCompletionPercentage()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("a legacy contract collected beyond its total is PAID, never negative, capped at 100%")
    void aLegacyContractCollectedBeyondItsTotalIsPaidAndCapped() {
        ClientPurchase created = this.createPurchase(new BigDecimal("100000.000"), new BigDecimal("100000.000"));

        // The service refuses to collect more than the total, so an over-collected row can only
        // come from data written before that rule existed. Forced here to prove the read path
        // copes with it.
        ClientPurchase stored = this.clientPurchaseRepository.findById(created.getId()).orElseThrow();
        stored.setPaidAmount(new BigDecimal("120000.000"));
        this.clientPurchaseRepository.saveAndFlush(stored);

        ClientPurchase purchase = this.clientPurchaseService.findById(created.getId());

        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(purchase.getRemainingAmount()).isEqualByComparingTo("0.000");
        assertThat(purchase.getCompletionPercentage()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("the collected amount is the direct payment plus every advance on the apartment")
    void theCollectedAmountIsTheDirectPaymentPlusEveryAdvance() {
        Apartment apartment = this.createApartment();
        this.createPurchaseFor(apartment, new BigDecimal("100000.000"), new BigDecimal("30000.000"));
        this.createAdvance(apartment, new BigDecimal("15000.000"));
        this.createAdvance(apartment, new BigDecimal("5000.000"));

        ClientPurchase purchase = this.clientPurchaseService.findById(
                this.clientPurchaseRepository.findByApartmentId(apartment.getId()).orElseThrow().getId());

        assertThat(purchase.getAdvanceAmount()).isEqualByComparingTo("20000.000");
        assertThat(purchase.getCollectedAmount()).isEqualByComparingTo("50000.000");
        assertThat(purchase.getRemainingAmount()).isEqualByComparingTo("50000.000");
        assertThat(purchase.getCompletionPercentage()).isEqualByComparingTo("50.000");
        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("the completion percentage is correct at scale 3")
    void theCompletionPercentageIsCorrectAtScaleThree() {
        // 1000 collected out of 3000 -> 33.333 %
        ClientPurchase purchase = this.createPurchase(new BigDecimal("3000.000"), new BigDecimal("1000.000"));

        assertThat(purchase.getCompletionPercentage()).isEqualByComparingTo("33.333");
    }

    @Test
    @DisplayName("a contract of zero completes at 0%, not by dividing by zero")
    void aContractOfZeroCompletesAtZeroPercent() {
        ClientPurchase purchase = this.createPurchase(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(purchase.getCompletionPercentage()).isEqualByComparingTo("0.000");
        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.UNPAID);
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
        Apartment apartment = this.createApartment();
        this.createAdvance(apartment, new BigDecimal("60000.000"));

        assertThatThrownBy(() -> this.createPurchaseFor(apartment, new BigDecimal("100000.000"),
                new BigDecimal("50000.000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dépasse le montant du contrat");
    }

    @Test
    @DisplayName("a direct payment plus advances landing exactly on the total is accepted")
    void aDirectPaymentPlusAdvancesLandingExactlyOnTheTotalIsAccepted() {
        Apartment apartment = this.createApartment();
        this.createAdvance(apartment, new BigDecimal("40000.000"));

        ClientPurchase purchase = this.createPurchaseFor(apartment, new BigDecimal("100000.000"),
                new BigDecimal("60000.000"));

        assertThat(purchase.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.PAID);
        assertThat(purchase.getRemainingAmount()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("an apartment cannot carry two sale contracts")
    void anApartmentCannotCarryTwoSaleContracts() {
        Apartment apartment = this.createApartment();
        this.createPurchaseFor(apartment, new BigDecimal("100000.000"), BigDecimal.ZERO);

        assertThatThrownBy(() -> this.createPurchaseFor(apartment, new BigDecimal("90000.000"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fait déjà l'objet d'un contrat de vente");
    }

    private ClientPurchase createPurchase(BigDecimal totalAmount, BigDecimal paidAmount) {
        return this.createPurchaseFor(this.createApartment(), totalAmount, paidAmount);
    }

    private ClientPurchase createPurchaseFor(Apartment apartment, BigDecimal totalAmount, BigDecimal paidAmount) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-CALC-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.getApartmentNumber());
        request.setTotalAmount(totalAmount);
        request.setPaidAmount(paidAmount);
        request.setClientId(this.client.getId());
        request.setProjectId(this.project.getId());
        request.setApartmentId(apartment.getId());
        return this.clientPurchaseService.create(request);
    }

    private void createAdvance(Apartment apartment, BigDecimal amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(amount);
        request.setApartmentId(apartment.getId());
        this.clientAdvanceService.create(request);
    }

    private Apartment createApartment() {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("P-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(this.project.getId());
        request.setAcquirerId(this.client.getId());
        return this.apartmentService.create(request);
    }

    private Project createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("CALC-PRJ");
        request.setName("Projet calculs");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private Client createClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur calculs");
        request.setProjectId(this.project.getId());
        return this.clientService.create(request);
    }
}
