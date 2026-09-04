package com.promoteur.app.service;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.SupplierPaymentRequest;
import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.dto.response.PayablesSummaryResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.dto.response.SupplierResponse;
import com.promoteur.app.enums.PaymentMethod;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.enums.SettlementFilter;
import com.promoteur.app.enums.SettlementStatus;
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
 * Covers supplier settlement (UX-04): what has been paid on an invoice is the sum of its
 * payments, its state follows from that, and an invoice is late only when it has a due date
 * that has passed with money still owed.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_payables;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupplierPaymentTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private SupplierInvoiceService supplierInvoiceService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private ProjectService projectService;

    private ProjectResponse project;
    private SupplierResponse supplier;

    @BeforeAll
    void seedProjectAndSupplier() {
        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setCode("PAY-PRJ");
        projectRequest.setName("Projet règlements");
        projectRequest.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(projectRequest);

        SupplierRequest supplierRequest = new SupplierRequest();
        supplierRequest.setName("Fournisseur règlements");
        this.supplier = this.supplierService.create(supplierRequest);
    }

    @Test
    @DisplayName("a new invoice is unpaid and owes its whole gross amount")
    void aNewInvoiceIsUnpaid() {
        SupplierInvoiceResponse invoice = this.createInvoice("1000.000", null);

        assertThat(invoice.status()).isEqualTo(SettlementStatus.UNPAID);
        assertThat(invoice.paidAmount()).isEqualByComparingTo("0.000");
        assertThat(invoice.remainingAmount()).isEqualByComparingTo(invoice.amountTtc());
        assertThat(invoice.overdue()).isFalse();
    }

    @Test
    @DisplayName("a part payment leaves the invoice partially paid")
    void aPartPaymentLeavesItPartiallyPaid() {
        SupplierInvoiceResponse invoice = this.createInvoice("1000.000", null);
        this.pay(invoice.id(), "300.000");

        SupplierInvoiceResponse reloaded = this.supplierInvoiceService.findById(invoice.id());
        assertThat(reloaded.status()).isEqualTo(SettlementStatus.PARTIALLY_PAID);
        assertThat(reloaded.paidAmount()).isEqualByComparingTo("300.000");
        assertThat(reloaded.remainingAmount()).isEqualByComparingTo(reloaded.amountTtc().subtract(new BigDecimal("300.000")));
    }

    @Test
    @DisplayName("payments accumulate, and settling the gross amount marks the invoice paid")
    void paymentsAccumulateUntilPaid() {
        SupplierInvoiceResponse invoice = this.createInvoice("1000.000", null);
        BigDecimal gross = invoice.amountTtc();

        this.pay(invoice.id(), "400.000");
        this.pay(invoice.id(), gross.subtract(new BigDecimal("400.000")).toPlainString());

        SupplierInvoiceResponse reloaded = this.supplierInvoiceService.findById(invoice.id());
        assertThat(reloaded.status()).isEqualTo(SettlementStatus.PAID);
        assertThat(reloaded.remainingAmount()).isEqualByComparingTo("0.000");
        assertThat(this.supplierInvoiceService.findPayments(invoice.id())).hasSize(2);
    }

    @Test
    @DisplayName("a payment that would exceed the invoice is refused")
    void aPaymentBeyondTheInvoiceIsRefused() {
        SupplierInvoiceResponse invoice = this.createInvoice("1000.000", null);

        assertThatThrownBy(() -> this.pay(invoice.id(), invoice.amountTtc().add(BigDecimal.ONE).toPlainString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alors que la facture s'élève à");
    }

    @Test
    @DisplayName("removing a payment puts the invoice back to what is left")
    void removingAPaymentRollsTheStateBack() {
        SupplierInvoiceResponse invoice = this.createInvoice("1000.000", null);
        Long paymentId = this.pay(invoice.id(), "500.000");

        this.supplierInvoiceService.deletePayment(invoice.id(), paymentId);

        SupplierInvoiceResponse reloaded = this.supplierInvoiceService.findById(invoice.id());
        assertThat(reloaded.status()).isEqualTo(SettlementStatus.UNPAID);
        assertThat(reloaded.paidAmount()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("an unpaid invoice past its due date is late, with the days counted")
    void anUnpaidInvoicePastItsDueDateIsLate() {
        SupplierInvoiceResponse invoice = this.createInvoice("2000.000", LocalDate.now().minusDays(20));

        SupplierInvoiceResponse reloaded = this.supplierInvoiceService.findById(invoice.id());
        assertThat(reloaded.overdue()).isTrue();
        assertThat(reloaded.daysLate()).isEqualTo(20);
    }

    @Test
    @DisplayName("an invoice without a due date is never late")
    void anInvoiceWithoutADueDateIsNeverLate() {
        SupplierInvoiceResponse invoice = this.createInvoice("2000.000", null);

        assertThat(this.supplierInvoiceService.findById(invoice.id()).overdue()).isFalse();
    }

    @Test
    @DisplayName("settling a late invoice clears the arrears")
    void settlingALateInvoiceClearsTheArrears() {
        SupplierInvoiceResponse invoice = this.createInvoice("2000.000", LocalDate.now().minusDays(5));
        this.pay(invoice.id(), invoice.amountTtc().toPlainString());

        SupplierInvoiceResponse reloaded = this.supplierInvoiceService.findById(invoice.id());
        assertThat(reloaded.overdue()).isFalse();
        assertThat(reloaded.daysLate()).isZero();
        assertThat(reloaded.status()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    @DisplayName("the payables summary separates what is owed from what is late")
    void thePayablesSummarySeparatesOwedFromLate() {
        // Its own data, so the assertions do not depend on the other tests running first.
        SupplierInvoiceResponse late = this.createInvoice("3000.000", LocalDate.now().minusDays(10));
        SupplierInvoiceResponse settled = this.createInvoice("1000.000", LocalDate.now().minusDays(10));
        this.pay(settled.id(), settled.amountTtc().toPlainString());

        PayablesSummaryResponse summary = this.supplierInvoiceService.payablesSummary(this.project.id());

        assertThat(summary.invoicedAmount()).isPositive();
        assertThat(summary.paidAmount()).isGreaterThanOrEqualTo(settled.amountTtc());
        assertThat(summary.dueAmount()).isGreaterThanOrEqualTo(late.amountTtc());
        assertThat(summary.overdueCount()).isPositive();
        // Every late invoice is also owed, so arrears can never exceed the outstanding total.
        assertThat(summary.overdueAmount()).isLessThanOrEqualTo(summary.dueAmount());
    }

    @Test
    @DisplayName("the list can be narrowed to one settlement state")
    void theListCanBeNarrowedToOneSettlementState() {
        SupplierInvoiceResponse invoice = this.createInvoice("700.000", null);
        this.pay(invoice.id(), invoice.amountTtc().toPlainString());

        assertThat(this.supplierInvoiceService.findBySettlement(
                        ListFilter.ofProject(this.project.id()), SettlementFilter.PAID, PageRequest.of(0, 200))
                .getContent())
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.status()).isEqualTo(SettlementStatus.PAID))
                .anySatisfy(row -> assertThat(row.id()).isEqualTo(invoice.id()));
    }

    @Test
    @DisplayName("the late filter keeps only invoices still owed past their due date")
    void theLateFilterKeepsOnlyInvoicesStillOwedPastTheirDueDate() {
        SupplierInvoiceResponse late = this.createInvoice("900.000", LocalDate.now().minusDays(20));
        SupplierInvoiceResponse settledLate = this.createInvoice("900.000", LocalDate.now().minusDays(20));
        this.pay(settledLate.id(), settledLate.amountTtc().toPlainString());
        SupplierInvoiceResponse notYetDue = this.createInvoice("900.000", LocalDate.now().plusDays(20));

        var rows = this.supplierInvoiceService.findBySettlement(
                ListFilter.ofProject(this.project.id()), SettlementFilter.OVERDUE, PageRequest.of(0, 200))
                .getContent();

        assertThat(rows).allSatisfy(row -> assertThat(row.overdue()).isTrue());
        assertThat(rows).anySatisfy(row -> assertThat(row.id()).isEqualTo(late.id()));
        assertThat(rows).noneSatisfy(row -> assertThat(row.id()).isEqualTo(settledLate.id()));
        assertThat(rows).noneSatisfy(row -> assertThat(row.id()).isEqualTo(notYetDue.id()));
    }

    private SupplierInvoiceResponse createInvoice(String amountHt, LocalDate dueDate) {
        SupplierInvoiceRequest request = new SupplierInvoiceRequest();
        request.setInvoiceNumber("FAC-PAY-" + this.sequence.incrementAndGet());
        request.setInvoiceDate(LocalDate.now().minusMonths(1));
        request.setDueDate(dueDate);
        request.setAmountHt(new BigDecimal(amountHt));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setSupplierId(this.supplier.id());
        request.setProjectId(this.project.id());
        return this.supplierInvoiceService.create(request);
    }

    private Long pay(Long invoiceId, String amount) {
        SupplierPaymentRequest request = new SupplierPaymentRequest();
        request.setPaymentDate(LocalDate.now());
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setReference("VIR-" + this.sequence.incrementAndGet());
        return this.supplierInvoiceService.addPayment(invoiceId, request).id();
    }
}
