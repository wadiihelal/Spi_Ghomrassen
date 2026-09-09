package com.promoteur.app.service;

import com.promoteur.app.AbstractIntegrationTest;
import com.promoteur.app.advance.ClientAdvanceRequest;
import com.promoteur.app.advance.ClientAdvanceService;
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
import com.promoteur.app.purchase.ClientPurchaseService;
import com.promoteur.app.report.ReportFilter;
import com.promoteur.app.report.ReportService;
import com.promoteur.app.shared.ListFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the query counts fixed in PERF-01 and PERF-03. Before this work, listing sale
 * contracts fetched three associations per row and ran one advance lookup per row, and the
 * client statement report ran three queries per client.
 */
class QueryCountTest extends AbstractIntegrationTest {

    private static final int CONTRACTS = 25;

    private final AtomicInteger sequence = new AtomicInteger();

    @PersistenceContext
    private EntityManager entityManager;

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
    private ReportService reportService;

    private ProjectResponse project;

    @BeforeAll
    void seedContracts() {
        this.project = this.createProject();
        for (int index = 0; index < CONTRACTS; index++) {
            ClientResponse client = this.createClient();
            ApartmentResponse apartment = this.createApartment(client);
            this.createPurchase(client, apartment);
            this.createAdvance(apartment);
        }
    }

    @Test
    @DisplayName("listing a page of 100 sale contracts stays under five queries")
    void listingAPageOfContractsStaysUnderFiveQueries() {
        long before = this.statementCount();

        assertThat(this.clientPurchaseService.findAll(ListFilter.none(), PageRequest.of(0, 100)).getContent())
                .hasSize(CONTRACTS);

        long issued = this.statementCount() - before;
        assertThat(issued).as("queries for a page of %d contracts", CONTRACTS).isLessThan(5);
    }

    @Test
    @DisplayName("the client statement report is a single query")
    void theClientStatementReportIsASingleQuery() {
        long before = this.statementCount();

        assertThat(this.reportService.clientStatements(ReportFilter.unrestricted(), PageRequest.of(0, 200))
                .getContent()).hasSize(CONTRACTS);

        assertThat(this.statementCount() - before).as("queries for the statement report").isEqualTo(1);
    }

    @Test
    @DisplayName("expense aggregates are grouped by the database, not in Java")
    void expenseAggregatesAreGroupedByTheDatabase() {
        long before = this.statementCount();

        this.reportService.expensesByCategory(ReportFilter.unrestricted(), PageRequest.of(0, 200));

        assertThat(this.statementCount() - before).as("queries for the category aggregate").isEqualTo(1);
    }

    @Test
    @DisplayName("listing apartments costs the same whether the page holds 5 rows or 20")
    void listingApartmentsCostsTheSameWhateverThePageSize() {
        long forFiveRows = this.queriesFor(() -> this.apartmentService.findAll(ListFilter.none(), PageRequest.of(0, 5)));
        long forTwentyRows = this.queriesFor(() -> this.apartmentService.findAll(ListFilter.none(), PageRequest.of(0, 20)));

        // The apartment page joins its associations and resolves contracts and advance totals in
        // two further grouped queries: bounded, and independent of the number of rows.
        assertThat(forTwentyRows).as("queries for a page of apartments").isEqualTo(forFiveRows).isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("listing sale contracts costs the same whether the page holds 5 rows or 20")
    void listingContractsCostsTheSameWhateverThePageSize() {
        long forFiveRows = this.queriesFor(() -> this.clientPurchaseService.findAll(ListFilter.none(), PageRequest.of(0, 5)));
        long forTwentyRows = this.queriesFor(() -> this.clientPurchaseService.findAll(ListFilter.none(), PageRequest.of(0, 20)));

        assertThat(forTwentyRows).as("queries for a page of contracts").isEqualTo(forFiveRows);
    }

    private long queriesFor(final Runnable work) {
        long before = this.statementCount();
        work.run();
        return this.statementCount() - before;
    }

    private long statementCount() {
        return this.statistics().getPrepareStatementCount();
    }

    private Statistics statistics() {
        return this.entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private ProjectResponse createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("QRY-PRJ");
        request.setName("Projet requêtes");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur " + this.sequence.incrementAndGet());
        request.setProjectId(this.project.id());
        return this.clientService.create(request);
    }

    private ApartmentResponse createApartment(ClientResponse client) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("Q-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(this.project.id());
        request.setAcquirerId(client.id());
        return this.apartmentService.create(request);
    }

    private void createPurchase(ClientResponse client, ApartmentResponse apartment) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-QRY-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(new BigDecimal("100000.000"));
        request.setPaidAmount(new BigDecimal("10000.000"));
        request.setClientId(client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartment.id());
        this.clientPurchaseService.create(request);
    }

    private void createAdvance(ApartmentResponse apartment) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(new BigDecimal("5000.000"));
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }
}
