package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientPurchaseResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ExpenseResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.enums.PurchasePaymentStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers PERF-02: the list endpoints filter and paginate in the database. The browser used to
 * request page 0 of 1000 rows per resource and filter in TypeScript, silently losing records
 * past the thousandth because nothing read totalElements.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_filters;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerSideFilteringTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ExpenseService expenseService;
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
    private ExpenseCategoryRepository expenseCategoryRepository;

    private ProjectResponse alpha;
    private ProjectResponse beta;

    @BeforeAll
    void seedTwoProjects() {
        this.alpha = this.createProject("FLT-A", "Résidence Alpha");
        this.beta = this.createProject("FLT-B", "Résidence Beta");

        this.createExpense(this.alpha, LocalDate.of(2026, 8, 10), "Ciment Alpha");
        this.createExpense(this.alpha, LocalDate.of(2026, 9, 10), "Carrelage Alpha");
        this.createExpense(this.beta, LocalDate.of(2026, 9, 10), "Ciment Beta");
    }

    @Test
    @DisplayName("a page reports the true total, so nothing is silently lost")
    void aPageReportsTheTrueTotal() {
        Page<ExpenseResponse> firstPage = this.expenseService.findAll(ListFilter.none(), PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(this.expenseService.findAll(ListFilter.none(), PageRequest.of(1, 2)).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("expenses filter by project in the database")
    void expensesFilterByProjectInTheDatabase() {
        assertThat(this.expenseService.findAll(ListFilter.ofProject(this.alpha.id()), PageRequest.of(0, 50)))
                .extracting(ExpenseResponse::projectName)
                .containsOnly("Résidence Alpha");
    }

    @Test
    @DisplayName("expenses filter by date range in the database")
    void expensesFilterByDateRangeInTheDatabase() {
        ListFilter september = new ListFilter(null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);

        assertThat(this.expenseService.findAll(september, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("free-text search matches the row's own labels and its project's name")
    void freeTextSearchMatchesLabelsAndProjectName() {
        ListFilter byDescription = new ListFilter(null, null, null, null, null, null, null, null, null, "carrelage");
        assertThat(this.expenseService.findAll(byDescription, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);

        ListFilter byProjectName = new ListFilter(null, null, null, null, null, null, null, null, null, "beta");
        assertThat(this.expenseService.findAll(byProjectName, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("sale contracts filter by their derived payment status, in SQL")
    void saleContractsFilterByDerivedPaymentStatus() {
        ClientResponse client = this.createClient(this.alpha, "Acquéreur statut");
        ApartmentResponse unpaidApartment = this.createApartment(this.alpha, client);
        ApartmentResponse partialApartment = this.createApartment(this.alpha, client);
        ApartmentResponse paidApartment = this.createApartment(this.alpha, client);

        this.createPurchase(client, unpaidApartment, "100000.000", "0.000");
        this.createPurchase(client, partialApartment, "100000.000", "10000.000");
        this.createPurchase(client, paidApartment, "100000.000", "100000.000");
        // An advance alone must also count towards the status.
        this.createAdvance(unpaidApartment, "1000.000");

        assertThat(this.byStatus(PurchasePaymentStatus.PAID))
                .extracting(ClientPurchaseResponse::apartmentNumber)
                .containsExactly(paidApartment.apartmentNumber());

        assertThat(this.byStatus(PurchasePaymentStatus.PARTIALLY_PAID))
                .extracting(ClientPurchaseResponse::apartmentNumber)
                .containsExactlyInAnyOrder(partialApartment.apartmentNumber(), unpaidApartment.apartmentNumber());

        assertThat(this.byStatus(PurchasePaymentStatus.UNPAID)).isEmpty();
    }

    @Test
    @DisplayName("an unknown payment status is refused at the boundary, so the API answers 400")
    void anUnknownPaymentStatusIsRefused() {
        assertThatThrownBy(() ->
                new ListFilter(null, null, null, null, null, "SOLDE", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Statut de paiement inconnu");
    }

    @Test
    @DisplayName("apartments filter by project and free text")
    void apartmentsFilterByProjectAndFreeText() {
        ClientResponse client = this.createClient(this.beta, "Acquéreur Beta");
        ApartmentResponse apartment = this.createApartment(this.beta, client);

        ListFilter filter = new ListFilter(this.beta.id(), null, null, null, null, null, null, null, null,
                apartment.apartmentNumber());

        assertThat(this.apartmentService.findAll(filter, PageRequest.of(0, 50)))
                .extracting(ApartmentResponse::apartmentNumber)
                .containsExactly(apartment.apartmentNumber());
    }

    private java.util.List<ClientPurchaseResponse> byStatus(PurchasePaymentStatus status) {
        ListFilter filter = new ListFilter(this.alpha.id(), null, null, null, null, status.name(), null,
                null, null, null);
        return this.clientPurchaseService.findAll(filter, PageRequest.of(0, 50)).getContent();
    }

    private ProjectResponse createProject(String code, String name) {
        ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName(name);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient(ProjectResponse project, String fullName) {
        ClientRequest request = new ClientRequest();
        request.setFullName(fullName);
        request.setProjectId(project.id());
        return this.clientService.create(request);
    }

    private ApartmentResponse createApartment(ProjectResponse project, ClientResponse client) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("F-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(project.id());
        request.setAcquirerId(client.id());
        return this.apartmentService.create(request);
    }

    private void createPurchase(ClientResponse client, ApartmentResponse apartment, String total, String paid) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-FLT-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(new BigDecimal(total));
        request.setPaidAmount(new BigDecimal(paid));
        request.setClientId(client.id());
        request.setProjectId(this.alpha.id());
        request.setApartmentId(apartment.id());
        this.clientPurchaseService.create(request);
    }

    private void createAdvance(ApartmentResponse apartment, String amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(new BigDecimal(amount));
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }

    private void createExpense(ProjectResponse project, LocalDate date, String description) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(date);
        request.setDescription(description);
        request.setAmountHt(new BigDecimal("1000.000"));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(project.id());
        this.expenseService.create(request);
    }
}
