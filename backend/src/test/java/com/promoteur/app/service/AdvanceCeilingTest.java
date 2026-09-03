package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.Project;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ClientAdvanceRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers CALC-03 and CONC-01: an advance is always capped by something, and two advances
 * submitted at the same instant can never both be accepted past the ceiling.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_ceiling;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=15000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvanceCeilingTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ClientAdvanceRepository clientAdvanceRepository;

    private Project project;
    private Client client;

    @BeforeAll
    void seedProjectAndClient() {
        this.project = this.createProject();
        this.client = this.createClient();
    }

    @Test
    @DisplayName("an apartment with neither a contract nor a sale price cannot take an advance")
    void anApartmentWithNeitherContractNorSalePriceCannotTakeAnAdvance() {
        Apartment apartment = this.createApartment(null);

        assertThatThrownBy(() -> this.createAdvance(apartment, new BigDecimal("1000.000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun contrat de vente ni prix de vente défini pour l'appartement")
                .hasMessageContaining(apartment.getApartmentNumber());
    }

    @Test
    @DisplayName("without a contract the advance is capped by the apartment's declared sale price")
    void withoutAContractTheAdvanceIsCappedByTheDeclaredSalePrice() {
        Apartment apartment = this.createApartment(new BigDecimal("100000.000"));

        this.createAdvance(apartment, new BigDecimal("60000.000"));

        assertThatThrownBy(() -> this.createAdvance(apartment, new BigDecimal("60000.000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alors que le plafond est de");
    }

    @Test
    @DisplayName("the refusal names the apartment and both amounts, so the toast can show them")
    void theRefusalNamesTheApartmentAndBothAmounts() {
        Apartment apartment = this.createApartment(new BigDecimal("100000.000"));

        assertThatThrownBy(() -> this.createAdvance(apartment, new BigDecimal("150000.000")))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(thrown -> {
                    String message = thrown.getMessage();
                    assertThat(message).contains(apartment.getApartmentNumber());
                    // Amounts are rendered in French: a non-breaking space groups the thousands.
                    assertThat(message.replace('\u00a0', ' ').replace('\u202f', ' '))
                            .contains("150 000,000")
                            .contains("100 000,000");
                });
    }

    @Test
    @DisplayName("an advance exactly reaching the sale price is accepted")
    void anAdvanceExactlyReachingTheSalePriceIsAccepted() {
        Apartment apartment = this.createApartment(new BigDecimal("50000.000"));

        ClientAdvance advance = this.createAdvance(apartment, new BigDecimal("50000.000"));

        assertThat(advance.getAmount()).isEqualByComparingTo("50000.000");
    }

    @Test
    @DisplayName("with a contract the advance is capped by the contract total, direct payment included")
    void withAContractTheAdvanceIsCappedByTheContractTotal() {
        Apartment apartment = this.createApartment(new BigDecimal("100000.000"));
        this.createPurchase(apartment, new BigDecimal("80000.000"), new BigDecimal("30000.000"));

        this.createAdvance(apartment, new BigDecimal("50000.000"));

        assertThatThrownBy(() -> this.createAdvance(apartment, new BigDecimal("0.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alors que le plafond est de");
    }

    @Test
    @DisplayName("editing an advance excludes itself from the other advances it is compared against")
    void editingAnAdvanceExcludesItselfFromTheComparison() {
        Apartment apartment = this.createApartment(new BigDecimal("100000.000"));
        ClientAdvance advance = this.createAdvance(apartment, new BigDecimal("90000.000"));

        ClientAdvanceRequest request = this.advanceRequest(apartment, new BigDecimal("95000.000"));
        request.setReference(advance.getReference());

        assertThat(this.clientAdvanceService.update(advance.getId(), request).getAmount())
                .isEqualByComparingTo("95000.000");
    }

    @Test
    @DisplayName("two advances of 60% of the contract fired at once produce one success and one refusal")
    void twoAdvancesOfSixtyPercentFiredAtOnceProduceOneSuccessAndOneRefusal() throws Exception {
        Apartment apartment = this.createApartment(new BigDecimal("100000.000"));
        this.createPurchase(apartment, new BigDecimal("100000.000"), BigDecimal.ZERO);
        BigDecimal sixtyPercent = new BigDecimal("60000.000");

        CountDownLatch startTogether = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            startTogether.await();
            try {
                this.createAdvance(apartment, sixtyPercent);
                return "accepted";
            } catch (RuntimeException ex) {
                return "refused: " + ex.getMessage();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes;
        try {
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt);
            startTogether.countDown();

            outcomes = List.of(first.get(), second.get());
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).as("exactly one of the two concurrent advances is accepted")
                .filteredOn("accepted"::equals).hasSize(1);
        // The refusal must be the ceiling check, not a lock timeout or an unrelated failure.
        assertThat(outcomes).filteredOn(outcome -> !"accepted".equals(outcome))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("alors que le plafond est de");

        List<ClientAdvance> stored = this.clientAdvanceRepository.findByApartmentId(apartment.getId());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getAmount()).isEqualByComparingTo(sixtyPercent);
    }

    private ClientAdvance createAdvance(Apartment apartment, BigDecimal amount) {
        return this.clientAdvanceService.create(this.advanceRequest(apartment, amount));
    }

    private ClientAdvanceRequest advanceRequest(Apartment apartment, BigDecimal amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(amount);
        request.setApartmentId(apartment.getId());
        return request;
    }

    private void createPurchase(Apartment apartment, BigDecimal totalAmount, BigDecimal paidAmount) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-CEIL-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.getApartmentNumber());
        request.setTotalAmount(totalAmount);
        request.setPaidAmount(paidAmount);
        request.setClientId(this.client.getId());
        request.setProjectId(this.project.getId());
        request.setApartmentId(apartment.getId());
        this.clientPurchaseService.create(request);
    }

    private Apartment createApartment(BigDecimal totalSalePrice) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("C-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(totalSalePrice);
        request.setProjectId(this.project.getId());
        request.setAcquirerId(this.client.getId());
        return this.apartmentService.create(request);
    }

    private Project createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("CEIL-PRJ");
        request.setName("Projet plafond");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private Client createClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur plafond");
        request.setProjectId(this.project.getId());
        return this.clientService.create(request);
    }
}
