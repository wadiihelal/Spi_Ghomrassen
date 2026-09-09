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
import com.promoteur.app.purchase.ClientPurchaseResponse;
import com.promoteur.app.purchase.ClientPurchaseService;
import com.promoteur.app.schedule.InstallmentLineRequest;
import com.promoteur.app.schedule.InstallmentStatus;
import com.promoteur.app.schedule.InstallmentSummaryResponse;
import com.promoteur.app.schedule.PaymentInstallmentResponse;
import com.promoteur.app.schedule.PaymentScheduleRequest;
import com.promoteur.app.schedule.PaymentScheduleResponse;
import com.promoteur.app.schedule.PaymentScheduleService;
import com.promoteur.app.schedule.ScheduleTemplateRequest;
import com.promoteur.app.shared.ListFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the payment schedule (UX-03). The rule that matters: money received on a contract is
 * spread across its instalments in order, so the plan and the cash can never disagree, and the
 * status of each line follows from its due date and its share.
 */
class PaymentScheduleTest extends AbstractIntegrationTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private PaymentScheduleService scheduleService;
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
    private ClientResponse client;

    @BeforeAll
    void seedProjectAndClient() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("SCHED-PRJ");
        request.setName("Projet échéancier");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(request);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setFullName("Acquéreur échéancier");
        clientRequest.setProjectId(this.project.id());
        this.client = this.clientService.create(clientRequest);
    }

    @Test
    @DisplayName("a template of 20/30/30/20 splits the contract exactly, spaced by the interval")
    void aTemplateSplitsTheContractExactly() {
        ClientPurchaseResponse purchase = this.createPurchase("100000.000", "0.000");

        PaymentScheduleResponse schedule = this.scheduleService.generate(purchase.id(),
                this.template(LocalDate.of(2026, 1, 15), 3, "20", "30", "30", "20"));

        assertThat(schedule.installments()).extracting(PaymentInstallmentResponse::amount)
                .containsExactly(new BigDecimal("20000.000"), new BigDecimal("30000.000"),
                        new BigDecimal("30000.000"), new BigDecimal("20000.000"));
        assertThat(schedule.installments()).extracting(PaymentInstallmentResponse::dueDate)
                .containsExactly(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 15),
                        LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15));
        assertThat(schedule.scheduledAmount()).isEqualByComparingTo("100000.000");
        assertThat(schedule.unscheduledAmount()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("the generated plan is named réservation, tranches and livraison by default")
    void theGeneratedPlanIsNamedInFrenchByDefault() {
        ClientPurchaseResponse purchase = this.createPurchase("90000.000", "0.000");

        assertThat(this.scheduleService.generate(purchase.id(),
                this.template(LocalDate.of(2026, 2, 1), 2, "25", "25", "25", "25")).installments())
                .extracting(PaymentInstallmentResponse::label)
                .containsExactly("Réservation", "Tranche 1", "Tranche 2", "Livraison");
    }

    @Test
    @DisplayName("a third of a contract that does not divide cleanly still sums to the total")
    void roundingRemainderLandsOnTheLastLine() {
        ClientPurchaseResponse purchase = this.createPurchase("100000.000", "0.000");

        PaymentScheduleResponse schedule = this.scheduleService.generate(purchase.id(),
                this.template(LocalDate.of(2026, 1, 1), 1, "33.333", "33.333", "33.334"));

        BigDecimal sum = schedule.installments().stream()
                .map(PaymentInstallmentResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100000.000");
        assertThat(schedule.unscheduledAmount()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("percentages that do not add up to 100 are refused")
    void percentagesThatDoNotAddUpAreRefused() {
        ClientPurchaseResponse purchase = this.createPurchase("100000.000", "0.000");

        assertThatThrownBy(() -> this.scheduleService.generate(purchase.id(),
                this.template(LocalDate.of(2026, 1, 1), 1, "20", "20")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au lieu de 100 %");
    }

    @Test
    @DisplayName("a schedule whose lines do not match the contract total is refused")
    void aScheduleThatDoesNotMatchTheContractIsRefused() {
        ClientPurchaseResponse purchase = this.createPurchase("100000.000", "0.000");

        PaymentScheduleRequest request = new PaymentScheduleRequest();
        request.setLines(List.of(this.line("Acompte", LocalDate.of(2026, 3, 1), "40000.000")));

        assertThatThrownBy(() -> this.scheduleService.replace(purchase.id(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Les deux doivent être égaux");
    }

    @Test
    @DisplayName("money received settles the earliest instalments first")
    void moneyReceivedSettlesTheEarliestInstalmentsFirst() {
        ApartmentResponse apartment = this.createApartment();
        // 25 000 paid directly on the contract, then a 10 000 advance: 35 000 collected.
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "100000.000", "25000.000");
        this.createAdvance(apartment, "10000.000");

        this.scheduleService.generate(purchase.id(),
                this.template(LocalDate.of(2026, 1, 15), 3, "20", "30", "30", "20"));
        PaymentScheduleResponse schedule = this.scheduleService.findByPurchase(purchase.id());

        assertThat(schedule.collectedAmount()).isEqualByComparingTo("35000.000");
        assertThat(schedule.installments()).extracting(PaymentInstallmentResponse::settledAmount)
                .containsExactly(new BigDecimal("20000.000"), new BigDecimal("15000.000"),
                        BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3));
        assertThat(schedule.installments()).extracting(PaymentInstallmentResponse::remainingAmount)
                .containsExactly(BigDecimal.ZERO.setScale(3), new BigDecimal("15000.000"),
                        new BigDecimal("30000.000"), new BigDecimal("20000.000"));
    }

    @Test
    @DisplayName("a settled line is PAID, an unsettled past line is OVERDUE, a future one UPCOMING")
    void statusFollowsFromTheDueDateAndTheShare() {
        ApartmentResponse apartment = this.createApartment();
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "100000.000", "20000.000");

        LocalDate today = LocalDate.now();
        PaymentScheduleRequest request = new PaymentScheduleRequest();
        request.setLines(List.of(
                this.line("Réservation", today.minusMonths(4), "20000.000"),
                this.line("Tranche 1", today.minusMonths(1), "30000.000"),
                this.line("Livraison", today.plusMonths(3), "50000.000")));
        this.scheduleService.replace(purchase.id(), request);

        List<PaymentInstallmentResponse> lines = this.scheduleService.findByPurchase(purchase.id()).installments();

        assertThat(lines).extracting(PaymentInstallmentResponse::status)
                .containsExactly(InstallmentStatus.PAID, InstallmentStatus.OVERDUE, InstallmentStatus.UPCOMING);
        assertThat(lines.get(1).daysLate()).isPositive();
        assertThat(lines.get(0).daysLate()).isZero();
    }

    @Test
    @DisplayName("a line part-covered before its due date is PARTIALLY_PAID, not overdue")
    void aPartlyCoveredFutureLineIsPartiallyPaid() {
        ApartmentResponse apartment = this.createApartment();
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "100000.000", "10000.000");

        PaymentScheduleRequest request = new PaymentScheduleRequest();
        request.setLines(List.of(
                this.line("Tranche unique", LocalDate.now().plusMonths(2), "100000.000")));
        this.scheduleService.replace(purchase.id(), request);

        PaymentInstallmentResponse line = this.scheduleService.findByPurchase(purchase.id()).installments().get(0);
        assertThat(line.status()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(line.settledAmount()).isEqualByComparingTo("10000.000");
    }

    @Test
    @DisplayName("overpaying the contract never leaves a negative remaining amount")
    void overpayingNeverGoesNegative() {
        ApartmentResponse apartment = this.createApartment();
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "50000.000", "50000.000");

        this.scheduleService.generate(purchase.id(), this.template(LocalDate.now().minusMonths(1), 1, "50", "50"));

        assertThat(this.scheduleService.findByPurchase(purchase.id()).installments())
                .allSatisfy(line -> {
                    assertThat(line.remainingAmount()).isEqualByComparingTo("0.000");
                    assertThat(line.status()).isEqualTo(InstallmentStatus.PAID);
                });
    }

    @Test
    @DisplayName("the summary reports what is late and what falls due this month")
    void theSummaryReportsLateAndDueThisMonth() {
        ApartmentResponse apartment = this.createApartment();
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "60000.000", "0.000");

        LocalDate today = LocalDate.now();
        PaymentScheduleRequest request = new PaymentScheduleRequest();
        request.setLines(List.of(
                this.line("En retard", today.minusMonths(2), "20000.000"),
                this.line("Ce mois", today.withDayOfMonth(Math.min(28, today.lengthOfMonth())), "25000.000"),
                this.line("Plus tard", today.plusMonths(6), "15000.000")));
        this.scheduleService.replace(purchase.id(), request);

        InstallmentSummaryResponse summary = this.scheduleService.summary(this.project.id(), today);

        assertThat(summary.overdueCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.overdueAmount()).isGreaterThanOrEqualTo(new BigDecimal("20000.000"));
        assertThat(summary.dueThisMonthCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.dueThisMonthAmount()).isGreaterThanOrEqualTo(new BigDecimal("25000.000"));
    }

    @Test
    @DisplayName("filtering by status keeps a line's settlement computed from the whole plan")
    void filteringByStatusKeepsSettlementFromTheWholePlan() {
        ApartmentResponse apartment = this.createApartment();
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "100000.000", "20000.000");

        LocalDate today = LocalDate.now();
        PaymentScheduleRequest request = new PaymentScheduleRequest();
        request.setLines(List.of(
                this.line("Réservation", today.minusMonths(3), "20000.000"),
                this.line("Solde", today.minusMonths(1), "80000.000")));
        this.scheduleService.replace(purchase.id(), request);

        List<PaymentInstallmentResponse> overdue = this.scheduleService.search(
                new ListFilter(this.project.id(), null, null, null, null, null, null, null, null, null),
                InstallmentStatus.OVERDUE);

        // The first line is settled by the 20 000 paid, so only the balance is late.
        assertThat(overdue).filteredOn(line -> line.purchaseId().equals(purchase.id()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.label()).isEqualTo("Solde");
                    assertThat(line.remainingAmount()).isEqualByComparingTo("80000.000");
                });
    }

    @Test
    @DisplayName("clearing the schedule leaves the payments alone")
    void clearingTheScheduleLeavesThePaymentsAlone() {
        ApartmentResponse apartment = this.createApartment();
        ClientPurchaseResponse purchase = this.createPurchaseFor(apartment, "40000.000", "10000.000");
        this.scheduleService.generate(purchase.id(), this.template(LocalDate.now(), 1, "50", "50"));

        this.scheduleService.clear(purchase.id());

        PaymentScheduleResponse schedule = this.scheduleService.findByPurchase(purchase.id());
        assertThat(schedule.installments()).isEmpty();
        assertThat(schedule.collectedAmount()).isEqualByComparingTo("10000.000");
        assertThat(schedule.unscheduledAmount()).isEqualByComparingTo("40000.000");
    }

    // --- helpers -----------------------------------------------------------------

    private ScheduleTemplateRequest template(LocalDate firstDueDate, int intervalMonths, String... percentages) {
        ScheduleTemplateRequest request = new ScheduleTemplateRequest();
        request.setFirstDueDate(firstDueDate);
        request.setIntervalMonths(intervalMonths);
        request.setLines(java.util.Arrays.stream(percentages).map(percentage -> {
            ScheduleTemplateRequest.TemplateLine line = new ScheduleTemplateRequest.TemplateLine();
            line.setPercentage(new BigDecimal(percentage));
            return line;
        }).toList());
        return request;
    }

    private InstallmentLineRequest line(String label, LocalDate dueDate, String amount) {
        InstallmentLineRequest request = new InstallmentLineRequest();
        request.setLabel(label);
        request.setDueDate(dueDate);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private ClientPurchaseResponse createPurchase(String total, String paid) {
        return this.createPurchaseFor(this.createApartment(), total, paid);
    }

    private ClientPurchaseResponse createPurchaseFor(ApartmentResponse apartment, String total, String paid) {
        ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setReference("PUR-SCHED-" + this.sequence.incrementAndGet());
        request.setPurchaseDate(LocalDate.of(2026, 1, 5));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(new BigDecimal(total));
        request.setPaidAmount(new BigDecimal(paid));
        request.setClientId(this.client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartment.id());
        return this.clientPurchaseService.create(request);
    }

    private void createAdvance(ApartmentResponse apartment, String amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 2, 1));
        request.setAmount(new BigDecimal(amount));
        request.setApartmentId(apartment.id());
        this.clientAdvanceService.create(request);
    }

    private ApartmentResponse createApartment() {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("E-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(new BigDecimal("100000.000"));
        request.setProjectId(this.project.id());
        request.setAcquirerId(this.client.id());
        return this.apartmentService.create(request);
    }
}
