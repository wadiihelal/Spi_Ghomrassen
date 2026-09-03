package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.report.ClientStatementDto;
import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the client statement arithmetic (TEST-01): what a client still owes is the total of
 * their contracts minus everything collected, whether collected as an advance or paid directly
 * on the contract.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_statements;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientStatementTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ReportService reportService;
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

    private ProjectResponse project;

    @BeforeAll
    void seedProject() {
        this.project = this.createProject();
    }

    @Test
    @DisplayName("what a client owes is their contracts minus advances and direct payments")
    void whatAClientOwesIsTheirContractsMinusAdvancesAndDirectPayments() {
        ClientResponse client = this.createClient("Acquéreur solde");
        ApartmentResponse apartment = this.createApartment(client);
        this.createPurchase(client, apartment, new BigDecimal("100000.000"), new BigDecimal("30000.000"));
        this.createAdvance(apartment, new BigDecimal("20000.000"));

        ClientStatementDto statement = this.statementFor(client);

        assertThat(statement.getTotalPurchases()).isEqualByComparingTo("100000.000");
        assertThat(statement.getTotalAdvances()).isEqualByComparingTo("50000.000");
        assertThat(statement.getRemainingToPay()).isEqualByComparingTo("50000.000");
    }

    @Test
    @DisplayName("a client whose contract is fully collected owes nothing")
    void aClientWhoseContractIsFullyCollectedOwesNothing() {
        ClientResponse client = this.createClient("Acquéreur soldé");
        ApartmentResponse apartment = this.createApartment(client);
        this.createPurchase(client, apartment, new BigDecimal("80000.000"), new BigDecimal("80000.000"));

        assertThat(this.statementFor(client).getRemainingToPay()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("a client with no contract at all shows zeros rather than nulls")
    void aClientWithNoContractShowsZeros() {
        ClientResponse client = this.createClient("Acquéreur sans contrat");

        ClientStatementDto statement = this.statementFor(client);

        assertThat(statement.getTotalPurchases()).isEqualByComparingTo("0.000");
        assertThat(statement.getTotalAdvances()).isEqualByComparingTo("0.000");
        assertThat(statement.getRemainingToPay()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("an advance alone, with no contract, counts as collected")
    void anAdvanceAloneCountsAsCollected() {
        ClientResponse client = this.createClient("Acquéreur acompte seul");
        ApartmentResponse apartment = this.createApartment(client);
        this.createAdvance(apartment, new BigDecimal("10000.000"));

        ClientStatementDto statement = this.statementFor(client);

        assertThat(statement.getTotalPurchases()).isEqualByComparingTo("0.000");
        assertThat(statement.getTotalAdvances()).isEqualByComparingTo("10000.000");
        // Nothing is owed yet because no contract declares an amount; the advance is a credit.
        assertThat(statement.getRemainingToPay()).isEqualByComparingTo("-10000.000");
    }

    @Test
    @DisplayName("the statement list is scoped like every other report")
    void theStatementListIsScopedLikeEveryOtherReport() {
        ClientResponse client = this.createClient("Acquéreur liste");
        ApartmentResponse apartment = this.createApartment(client);
        this.createPurchase(client, apartment, new BigDecimal("60000.000"), new BigDecimal("10000.000"));

        assertThat(this.reportService.clientStatements(
                        ReportFilter.of(String.valueOf(this.project.id()), null, null), PageRequest.of(0, 100))
                .getContent())
                .extracting(ClientStatementDto::getClientName)
                .contains("Acquéreur liste");
    }

    private ClientStatementDto statementFor(ClientResponse client) {
        return this.reportService.clientStatement(client.id(), ReportFilter.unrestricted());
    }

    private void createPurchase(ClientResponse client, ApartmentResponse apartment, BigDecimal totalAmount, BigDecimal paidAmount) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-STMT-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(totalAmount);
        request.setPaidAmount(paidAmount);
        request.setClientId(client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartment.id());
        this.clientPurchaseService.create(request);
    }

    private void createAdvance(ApartmentResponse apartment, BigDecimal amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(amount);
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }

    private ApartmentResponse createApartment(ClientResponse client) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("S-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(this.project.id());
        request.setAcquirerId(client.id());
        return this.apartmentService.create(request);
    }

    private ProjectResponse createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("STMT-PRJ");
        request.setName("Projet situations");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient(String fullName) {
        ClientRequest request = new ClientRequest();
        request.setFullName(fullName);
        request.setProjectId(this.project.id());
        return this.clientService.create(request);
    }
}
