package com.promoteur.app.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promoteur.app.AbstractIntegrationTest;
import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SupplierResponse;
import com.promoteur.app.enums.PaymentMethod;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.service.ApartmentService;
import com.promoteur.app.service.ClientAdvanceService;
import com.promoteur.app.service.ClientPurchaseService;
import com.promoteur.app.service.ClientService;
import com.promoteur.app.service.ExpenseService;
import com.promoteur.app.service.ProjectService;
import com.promoteur.app.service.SupplierInvoiceService;
import com.promoteur.app.service.SupplierService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the DTO boundary against a lazy load escaping the transaction (PERF-03, and the
 * « DTO à la frontière » rule of CLAUDE.md).
 *
 * <p>A response record is meant to carry flat identifiers and labels — {@code projectId} plus
 * {@code projectName} — and never a JPA entity. Break that and nothing fails at once: the
 * service returns fine, and the failure appears when Jackson walks the object graph while
 * serialising the HTTP response, by then outside the {@code readOnly} transaction. The symptom
 * is a 500 on a list endpoint that worked in every service test.</p>
 *
 * <p>So this class does what the web layer does: it takes what each finder returns and asks
 * Jackson to write it, outside any transaction. A {@code LazyInitializationException} here names
 * the DTO at fault.</p>
 */
class ResponseSerializationTest extends AbstractIntegrationTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private SupplierInvoiceService supplierInvoiceService;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    @BeforeAll
    void seedOneOfEach() {
        final ProjectResponse project = this.createProject();
        final ClientResponse client = this.createClient(project);
        final SupplierResponse supplier = this.createSupplier();
        final ApartmentResponse apartment = this.createApartment(project, client);

        this.createExpense(project, supplier);
        this.createInvoice(project, supplier);
        this.createPurchase(project, client, apartment);
        this.createAdvance(apartment);
    }

    @Test
    @DisplayName("no list endpoint response triggers a lazy load once the transaction is closed")
    void noListResponseTriggersALazyLoadOutsideTheTransaction() {
        this.assertSerialises("expenses", () -> this.expenseService.findAll(ListFilter.none(), FIRST_PAGE));
        this.assertSerialises("apartments", () -> this.apartmentService.findAll(ListFilter.none(), FIRST_PAGE));
        this.assertSerialises("purchases", () -> this.clientPurchaseService.findAll(ListFilter.none(), FIRST_PAGE));
        this.assertSerialises("advances", () -> this.clientAdvanceService.findAll(ListFilter.none(), FIRST_PAGE));
        this.assertSerialises("clients", () -> this.clientService.findAll(null, FIRST_PAGE));
        this.assertSerialises("suppliers", () -> this.supplierService.findAll(FIRST_PAGE));
        this.assertSerialises("projects", () -> this.projectService.findAll(FIRST_PAGE));
    }

    @Test
    @DisplayName("a response carries flat identifiers and labels, never a nested entity")
    void aResponseCarriesFlatIdentifiersAndLabels() throws Exception {
        final Page<?> expenses = this.expenseService.findAll(ListFilter.none(), FIRST_PAGE);
        final String json = this.objectMapper.writeValueAsString(expenses.getContent().get(0));

        // The console reads projectId next to projectName; a nested object would mean the entity
        // itself crossed the boundary.
        assertThat(json).contains("\"projectId\"").contains("\"projectName\"");
        assertThat(json).doesNotContain("\"project\":{").doesNotContain("\"category\":{");
        assertThat(json).doesNotContain("\"supplier\":{").doesNotContain("hibernateLazyInitializer");
    }

    /** Serialises what a finder returned, outside any transaction, and names it on failure. */
    private void assertSerialises(final String endpoint, final Supplier<Page<?>> finder) {
        final Page<?> page = finder.get();
        assertThat(page.getContent()).as("%s must have a row to serialise", endpoint).isNotEmpty();

        assertThatCode(() -> this.objectMapper.writeValueAsString(page.getContent()))
                .as("serialising the %s response outside the transaction", endpoint)
                .doesNotThrowAnyException();
    }

    private ProjectResponse createProject() {
        final ProjectRequest request = new ProjectRequest();
        request.setCode("SER-PRJ");
        request.setName("Projet sérialisation");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient(final ProjectResponse project) {
        final ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur sérialisation");
        request.setProjectId(project.id());
        return this.clientService.create(request);
    }

    private SupplierResponse createSupplier() {
        final SupplierRequest request = new SupplierRequest();
        request.setName("Fournisseur sérialisation");
        return this.supplierService.create(request);
    }

    private ApartmentResponse createApartment(final ProjectResponse project, final ClientResponse client) {
        final ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("SER-A1");
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(project.id());
        request.setAcquirerId(client.id());
        return this.apartmentService.create(request);
    }

    private void createExpense(final ProjectResponse project, final SupplierResponse supplier) {
        final ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(LocalDate.of(2026, 9, 1));
        request.setDescription("Dépense sérialisation");
        request.setAmountHt(new BigDecimal("1000.000"));
        request.setVatRate(new BigDecimal("0.0700"));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(project.id());
        request.setSupplierId(supplier.id());
        this.expenseService.create(request);
    }

    private void createInvoice(final ProjectResponse project, final SupplierResponse supplier) {
        final SupplierInvoiceRequest request = new SupplierInvoiceRequest();
        request.setInvoiceNumber("SER-INV-1");
        request.setInvoiceDate(LocalDate.of(2026, 9, 1));
        request.setAmountHt(new BigDecimal("1000.000"));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setSupplierId(supplier.id());
        request.setProjectId(project.id());
        this.supplierInvoiceService.create(request);
    }

    private void createPurchase(final ProjectResponse project, final ClientResponse client,
                                final ApartmentResponse apartment) {
        final ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(new BigDecimal("100000.000"));
        request.setPaidAmount(new BigDecimal("10000.000"));
        request.setClientId(client.id());
        request.setProjectId(project.id());
        request.setApartmentId(apartment.id());
        this.clientPurchaseService.create(request);
    }

    /** Only the lot is sent: the service derives the client and the project from it. */
    private void createAdvance(final ApartmentResponse apartment) {
        final ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 15));
        request.setAmount(new BigDecimal("5000.000"));
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }
}
