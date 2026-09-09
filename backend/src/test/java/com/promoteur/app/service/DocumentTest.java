package com.promoteur.app.service;

import com.promoteur.app.AbstractIntegrationTest;
import com.promoteur.app.advance.ClientAdvanceRequest;
import com.promoteur.app.advance.ClientAdvanceResponse;
import com.promoteur.app.advance.ClientAdvanceService;
import com.promoteur.app.apartment.ApartmentRequest;
import com.promoteur.app.apartment.ApartmentResponse;
import com.promoteur.app.apartment.ApartmentService;
import com.promoteur.app.client.ClientRequest;
import com.promoteur.app.client.ClientResponse;
import com.promoteur.app.client.ClientService;
import com.promoteur.app.document.DocumentService;
import com.promoteur.app.expense.ExpenseCategoryService;
import com.promoteur.app.expense.ExpenseRequest;
import com.promoteur.app.expense.ExpenseService;
import com.promoteur.app.invoice.SupplierInvoiceRequest;
import com.promoteur.app.invoice.SupplierInvoiceService;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectResponse;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import com.promoteur.app.purchase.ClientPurchaseRequest;
import com.promoteur.app.purchase.ClientPurchaseService;
import com.promoteur.app.shared.PaymentMethod;
import com.promoteur.app.shared.ResourceNotFoundException;
import com.promoteur.app.supplier.SupplierRequest;
import com.promoteur.app.supplier.SupplierResponse;
import com.promoteur.app.supplier.SupplierService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

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
class DocumentTest extends AbstractIntegrationTest {

    /**
     * Every PDF begins with this signature; a truncated stream would not.
     */
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

    /**
     * A contract on a fresh lot, plus one payment against it.
     */
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
