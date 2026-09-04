package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientPurchaseResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
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

/**
 * Covers the sale contract's reference (UX-09): allocated by sequence when the form leaves it
 * blank, kept when the user brings one, and never changed by an update.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_purchase_ref;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseReferenceTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;

    private ProjectResponse project;
    private ClientResponse client;

    @BeforeAll
    void seed() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("REF-PRJ");
        request.setName("Projet références");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(request);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setFullName("Acquéreur références");
        clientRequest.setProjectId(this.project.id());
        this.client = this.clientService.create(clientRequest);
    }

    @Test
    @DisplayName("a contract saved without a reference gets ACH-<year>-<number>")
    void aContractSavedWithoutAReferenceGetsANumber() {
        ClientPurchaseResponse purchase = this.create(null, LocalDate.of(2026, 4, 2));

        assertThat(purchase.reference()).matches("ACH-2026-\\d{5}");
    }

    @Test
    @DisplayName("two contracts in a row get increasing numbers")
    void twoContractsInARowGetIncreasingNumbers() {
        ClientPurchaseResponse first = this.create("", LocalDate.of(2026, 4, 2));
        ClientPurchaseResponse second = this.create("   ", LocalDate.of(2026, 4, 3));

        int firstNumber = Integer.parseInt(first.reference().substring(first.reference().lastIndexOf('-') + 1));
        int secondNumber = Integer.parseInt(second.reference().substring(second.reference().lastIndexOf('-') + 1));
        assertThat(secondNumber).isGreaterThan(firstNumber);
    }

    @Test
    @DisplayName("a reference brought by the user is kept as typed")
    void aReferenceBroughtByTheUserIsKept() {
        assertThat(this.create("  CONTRAT-NOTAIRE-77 ", LocalDate.of(2026, 4, 2)).reference())
                .isEqualTo("CONTRAT-NOTAIRE-77");
    }

    @Test
    @DisplayName("updating a contract without a reference keeps the one it already has")
    void updatingAContractKeepsItsReference() {
        ClientPurchaseResponse purchase = this.create(null, LocalDate.of(2026, 4, 2));

        ClientPurchaseRequest update = this.request(null, LocalDate.of(2026, 4, 2), purchase.apartmentId());
        update.setTotalAmount(new BigDecimal("99000.000"));
        ClientPurchaseResponse updated = this.clientPurchaseService.update(purchase.id(), update);

        assertThat(updated.reference()).isEqualTo(purchase.reference());
        assertThat(updated.totalAmount()).isEqualByComparingTo("99000.000");
    }

    private ClientPurchaseResponse create(String reference, LocalDate date) {
        return this.clientPurchaseService.create(this.request(reference, date, this.apartment().id()));
    }

    private ClientPurchaseRequest request(String reference, LocalDate date, Long apartmentId) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference(reference);
        request.setPurchaseDate(date);
        request.setAssetDescription("Lot de test");
        request.setTotalAmount(new BigDecimal("120000.000"));
        request.setPaidAmount(BigDecimal.ZERO);
        request.setClientId(this.client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartmentId);
        return request;
    }

    private ApartmentResponse apartment() {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("R-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("120000.000"));
        request.setProjectId(this.project.id());
        request.setAcquirerId(this.client.id());
        return this.apartmentService.create(request);
    }
}
