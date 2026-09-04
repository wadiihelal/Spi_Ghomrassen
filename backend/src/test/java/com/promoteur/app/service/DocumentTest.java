package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientPurchaseRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.dto.response.ClientAdvanceResponse;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SupplierResponse;
import com.promoteur.app.enums.PaymentMethod;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.exception.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the printed documents (UX-06): each one is produced as a real PDF from the stored
 * figures, printing never changes a balance, and an unknown id is refused rather than printed
 * blank.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_documents;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentTest {

    /** Every PDF begins with this signature; a truncated stream would not. */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private DocumentService documentService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private SupplierInvoiceService supplierInvoiceService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private ExpenseCategoryService expenseCategoryService;

    private ProjectResponse project;
    private ClientResponse client;
    private SupplierResponse supplier;

    @BeforeAll
    void seed() {
        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setCode("DOC-PRJ");
        projectRequest.setName("Projet documents");
        projectRequest.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(projectRequest);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setFullName("Acquéreur documents");
        clientRequest.setCinOrFiscalId("01234567");
        clientRequest.setPhone("+216 75 000 000");
        clientRequest.setAddress("Ghomrassen");
        clientRequest.setProjectId(this.project.id());
        this.client = this.clientService.create(clientRequest);

        SupplierRequest supplierRequest = new SupplierRequest();
        supplierRequest.setName("Fournisseur documents");
        this.supplier = this.supplierService.create(supplierRequest);
    }

    @Test
    @DisplayName("a receipt is produced as a PDF for a payment collected")
    void aReceiptIsProducedAsAPdf() {
        ClientAdvanceResponse advance = this.collect("15000.000");

        byte[] receipt = this.documentService.paymentReceipt(advance.id());

        assertThat(receipt).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    @DisplayName("printing a receipt twice leaves the payment untouched")
    void printingAReceiptTwiceLeavesThePaymentUntouched() {
        ClientAdvanceResponse advance = this.collect("12000.000");

        this.documentService.paymentReceipt(advance.id());
        this.documentService.paymentReceipt(advance.id());

        assertThat(this.clientAdvanceService.findById(advance.id()).amount())
                .isEqualByComparingTo(advance.amount());
    }

    @Test
    @DisplayName("a receipt for an unknown payment is refused")
    void aReceiptForAnUnknownPaymentIsRefused() {
        assertThatThrownBy(() -> this.documentService.paymentReceipt(9_999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a statement is produced for a client with contracts and payments")
    void aStatementIsProducedForAClient() {
        this.collect("9000.000");

        byte[] statement = this.documentService.clientStatement(this.client.id());

        assertThat(statement).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    @DisplayName("a statement is still produced for a client with nothing recorded")
    void aStatementIsStillProducedForAnEmptyClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Client sans mouvement");
        request.setProjectId(this.project.id());
        ClientResponse quiet = this.clientService.create(request);

        assertThat(this.documentService.clientStatement(quiet.id())).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    @DisplayName("a statement for an unknown client is refused")
    void aStatementForAnUnknownClientIsRefused() {
        assertThatThrownBy(() -> this.documentService.clientStatement(9_999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the VAT recap is produced for a month with expenses and supplier invoices")
    void theVatRecapIsProducedForABusyMonth() {
        LocalDate when = LocalDate.of(2026, 5, 12);
        this.recordExpense(when, "10000.000", "0.1900");
        this.recordInvoice(when, "8000.000", "0.1300");

        byte[] recap = this.documentService.vatSummary(when.getYear(), when.getMonthValue(), this.project.id());

        assertThat(recap).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    @DisplayName("the VAT recap of an empty month is produced rather than refused")
    void theVatRecapOfAnEmptyMonthIsStillProduced() {
        assertThat(this.documentService.vatSummary(2019, 1, this.project.id()))
                .isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    @DisplayName("the VAT recap covers every project when no project is given")
    void theVatRecapCoversEveryProjectWhenNoProjectIsGiven() {
        LocalDate when = LocalDate.of(2026, 6, 3);
        this.recordExpense(when, "5000.000", "0.0700");

        assertThat(this.documentService.vatSummary(when.getYear(), when.getMonthValue(), null))
                .isNotEmpty().startsWith(PDF_MAGIC);
    }

    // --- fixtures -------------------------------------------------------------

    /** A contract on a fresh lot, plus one payment against it. */
    private ClientAdvanceResponse collect(String amount) {
        ApartmentRequest apartmentRequest = new ApartmentRequest();
        apartmentRequest.setApartmentNumber("D-" + this.sequence.incrementAndGet());
        apartmentRequest.setApartmentType("S+2");
        apartmentRequest.setTotalSurface(new BigDecimal("100.000"));
        apartmentRequest.setTotalSalePrice(new BigDecimal("120000.000"));
        apartmentRequest.setProjectId(this.project.id());
        apartmentRequest.setAcquirerId(this.client.id());
        ApartmentResponse apartment = this.apartmentService.create(apartmentRequest);

        ClientPurchaseRequest purchaseRequest = new ClientPurchaseRequest();
        purchaseRequest.setReference("PUR-DOC-" + this.sequence.incrementAndGet());
        purchaseRequest.setPurchaseDate(LocalDate.of(2026, 2, 10));
        purchaseRequest.setAssetDescription("Appartement " + apartment.apartmentNumber());
        purchaseRequest.setTotalAmount(new BigDecimal("120000.000"));
        purchaseRequest.setPaidAmount(BigDecimal.ZERO);
        purchaseRequest.setClientId(this.client.id());
        purchaseRequest.setProjectId(this.project.id());
        purchaseRequest.setApartmentId(apartment.id());
        this.clientPurchaseService.create(purchaseRequest);

        ClientAdvanceRequest advanceRequest = new ClientAdvanceRequest();
        advanceRequest.setAdvanceDate(LocalDate.of(2026, 3, 1));
        advanceRequest.setAmount(new BigDecimal(amount));
        advanceRequest.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        advanceRequest.setApartmentId(apartment.id());
        advanceRequest.setNotes("Premier versement.");
        return this.clientAdvanceService.create(advanceRequest);
    }

    private void recordExpense(LocalDate when, String amountHt, String vatRate) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(when);
        request.setDescription("Dépense documents " + this.sequence.incrementAndGet());
        request.setAmountHt(new BigDecimal(amountHt));
        request.setVatRate(new BigDecimal(vatRate));
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setProjectId(this.project.id());
        request.setCategoryId(this.expenseCategoryService.findAll(PageRequest.of(0, 1))
                .getContent().get(0).id());
        this.expenseService.create(request);
    }

    private void recordInvoice(LocalDate when, String amountHt, String vatRate) {
        SupplierInvoiceRequest request = new SupplierInvoiceRequest();
        request.setInvoiceNumber("FAC-DOC-" + this.sequence.incrementAndGet());
        request.setInvoiceDate(when);
        request.setAmountHt(new BigDecimal(amountHt));
        request.setVatRate(new BigDecimal(vatRate));
        request.setSupplierId(this.supplier.id());
        request.setProjectId(this.project.id());
        this.supplierInvoiceService.create(request);
    }
}
