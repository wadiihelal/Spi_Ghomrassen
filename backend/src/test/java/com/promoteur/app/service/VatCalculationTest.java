package com.promoteur.app.service;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.dto.response.ExpenseResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SupplierResponse;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.dto.response.VatRateOptionResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers CALC-01: any Tunisian VAT rate can be entered, and the backend — not the browser —
 * derives the VAT and gross amounts at scale 3.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_vat;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VatCalculationTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private SupplierInvoiceService supplierInvoiceService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private VatRateOptionService vatRateOptionService;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    private ProjectResponse project;
    private SupplierResponse supplier;

    @BeforeAll
    void seedProjectAndSupplier() {
        this.project = this.createProject();
        this.supplier = this.createSupplier(new BigDecimal("0.0700"));
    }

    @Test
    @DisplayName("the four Tunisian VAT rates are reference data, not a hard-coded list")
    void theFourTunisianVatRatesAreReferenceData() {
        assertThat(this.vatRateOptionService.findAll(PageRequest.of(0, 50)).getContent())
                .extracting(VatRateOptionResponse::rate)
                .containsExactly(
                        new BigDecimal("0.0000"),
                        new BigDecimal("0.0700"),
                        new BigDecimal("0.1300"),
                        new BigDecimal("0.1900"));
    }

    @Test
    @DisplayName("an expense of 1000.000 HT at 7% saves as TVA 70.000 and TTC 1070.000")
    void anExpenseAtSevenPercentSavesSeventyAndOneThousandSeventy() {
        ExpenseResponse expense = this.createExpense(new BigDecimal("1000.000"), new BigDecimal("0.0700"));

        assertThat(expense.vatRate()).isEqualByComparingTo("0.0700");
        assertThat(expense.vatAmount()).isEqualByComparingTo("70.000");
        assertThat(expense.amountTtc()).isEqualByComparingTo("1070.000");
    }

    @Test
    @DisplayName("an exempt supply at 0% saves as TVA 0.000 and TTC equal to the net amount")
    void anExemptSupplySavesZeroVat() {
        ExpenseResponse expense = this.createExpense(new BigDecimal("1000.000"), BigDecimal.ZERO);

        assertThat(expense.vatAmount()).isEqualByComparingTo("0.000");
        assertThat(expense.amountTtc()).isEqualByComparingTo("1000.000");
    }

    @Test
    @DisplayName("each configured rate rounds to the millime, half up")
    void eachConfiguredRateRoundsToTheMillimeHalfUp() {
        // 333.335 x 13% = 43.33355 -> 43.334
        ExpenseResponse expense = this.createExpense(new BigDecimal("333.335"), new BigDecimal("0.1300"));

        assertThat(expense.vatAmount()).isEqualByComparingTo("43.334");
        assertThat(expense.amountTtc()).isEqualByComparingTo("376.669");
    }

    @Test
    @DisplayName("a hand-crafted payload whose declared VAT contradicts the rate is rejected")
    void aHandCraftedPayloadWhoseDeclaredVatContradictsTheRateIsRejected() {
        ExpenseRequest request = this.expenseRequest(new BigDecimal("1000.000"), new BigDecimal("0.0700"));
        request.setVatAmount(new BigDecimal("190.000"));

        assertThatThrownBy(() -> this.expenseService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne correspond pas au taux");
    }

    @Test
    @DisplayName("a hand-crafted payload whose declared TTC contradicts the rate is rejected")
    void aHandCraftedPayloadWhoseDeclaredTtcContradictsTheRateIsRejected() {
        ExpenseRequest request = this.expenseRequest(new BigDecimal("1000.000"), new BigDecimal("0.0700"));
        request.setAmountTtc(new BigDecimal("1190.000"));

        assertThatThrownBy(() -> this.expenseService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doit être égal au montant HT plus la TVA");
    }

    @ParameterizedTest(name = "an expense at rate {0} keeps TTC equal to HT plus TVA")
    @ValueSource(strings = {"0.0000", "0.0700", "0.1300", "0.1900"})
    @DisplayName("at every configured rate, an expense keeps TTC equal to HT plus TVA")
    void atEveryConfiguredRateAnExpenseKeepsTtcEqualToHtPlusVat(String rate) {
        BigDecimal amountHt = new BigDecimal("1234.567");
        ExpenseResponse expense = this.createExpense(amountHt, new BigDecimal(rate));

        BigDecimal expectedVat = amountHt.multiply(new BigDecimal(rate)).setScale(3, RoundingMode.HALF_UP);
        assertThat(expense.vatAmount()).isEqualByComparingTo(expectedVat);
        assertThat(expense.amountTtc()).isEqualByComparingTo(amountHt.add(expectedVat));
    }

    @ParameterizedTest(name = "a supplier invoice at rate {0} keeps TTC equal to HT plus TVA")
    @ValueSource(strings = {"0.0000", "0.0700", "0.1300", "0.1900"})
    @DisplayName("at every configured rate, a supplier invoice keeps TTC equal to HT plus TVA")
    void atEveryConfiguredRateASupplierInvoiceKeepsTtcEqualToHtPlusVat(String rate) {
        BigDecimal amountHt = new BigDecimal("987.654");
        SupplierInvoiceRequest request = new SupplierInvoiceRequest();
        request.setInvoiceNumber("FAC-RATE-" + rate + "-" + this.sequence.incrementAndGet());
        request.setInvoiceDate(LocalDate.of(2026, 9, 1));
        request.setAmountHt(amountHt);
        request.setVatRate(new BigDecimal(rate));
        request.setSupplierId(this.supplier.id());
        request.setProjectId(this.project.id());

        SupplierInvoiceResponse invoice = this.supplierInvoiceService.create(request);

        BigDecimal expectedVat = amountHt.multiply(new BigDecimal(rate)).setScale(3, RoundingMode.HALF_UP);
        assertThat(invoice.vatAmount()).isEqualByComparingTo(expectedVat);
        assertThat(invoice.amountTtc()).isEqualByComparingTo(amountHt.add(expectedVat));
    }

    @Test
    @DisplayName("a supplier invoice at 13% is derived by the backend and carries no withholding")
    void aSupplierInvoiceAtThirteenPercentIsDerivedByTheBackend() {
        SupplierInvoiceRequest request = new SupplierInvoiceRequest();
        request.setInvoiceNumber("FAC-VAT-" + this.sequence.incrementAndGet());
        request.setInvoiceDate(LocalDate.of(2026, 9, 1));
        request.setAmountHt(new BigDecimal("2000.000"));
        request.setVatRate(new BigDecimal("0.1300"));
        request.setSupplierId(this.supplier.id());
        request.setProjectId(this.project.id());

        SupplierInvoiceResponse invoice = this.supplierInvoiceService.create(request);

        assertThat(invoice.vatAmount()).isEqualByComparingTo("260.000");
        assertThat(invoice.amountTtc()).isEqualByComparingTo("2260.000");
    }

    @Test
    @DisplayName("a supplier carries the rate usually invoiced, so data entry can default to it")
    void aSupplierCarriesTheRateUsuallyInvoiced() {
        assertThat(this.supplier.defaultVatRate()).isEqualByComparingTo("0.0700");
    }

    private ExpenseResponse createExpense(BigDecimal amountHt, BigDecimal vatRate) {
        return this.expenseService.create(this.expenseRequest(amountHt, vatRate));
    }

    private ExpenseRequest expenseRequest(BigDecimal amountHt, BigDecimal vatRate) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(LocalDate.of(2026, 9, 1));
        request.setDescription("Dépense TVA " + this.sequence.incrementAndGet());
        request.setAmountHt(amountHt);
        request.setVatRate(vatRate);
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(this.project.id());
        return request;
    }

    private ProjectResponse createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("VAT-PRJ");
        request.setName("Projet TVA");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private SupplierResponse createSupplier(BigDecimal defaultVatRate) {
        SupplierRequest request = new SupplierRequest();
        request.setName("Fournisseur TVA");
        request.setDefaultVatRate(defaultVatRate);
        return this.supplierService.create(request);
    }
}
