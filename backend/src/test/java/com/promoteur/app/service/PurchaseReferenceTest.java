package com.promoteur.app.service;

import com.promoteur.app.AbstractIntegrationTest;
import com.promoteur.app.apartment.ApartmentRequest;
import com.promoteur.app.apartment.ApartmentResponse;
import com.promoteur.app.apartment.ApartmentService;
import com.promoteur.app.client.ClientRequest;
import com.promoteur.app.client.ClientResponse;
import com.promoteur.app.client.ClientService;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectResponse;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import com.promoteur.app.purchase.ClientPurchaseRequest;
import com.promoteur.app.purchase.ClientPurchaseResponse;
import com.promoteur.app.purchase.ClientPurchaseService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the sale contract's reference (UX-09): allocated by sequence when the form leaves it
 * blank, kept when the user brings one, and never changed by an update.
 */
class PurchaseReferenceTest extends AbstractIntegrationTest {

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
