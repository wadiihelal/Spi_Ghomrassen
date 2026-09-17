package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
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
import com.promoteur.app.schedule.PaymentInstallmentResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the «&nbsp;échéancier&nbsp;» search against PostgreSQL, bounds included.
 *
 * <h2>Why it exists</h2>
 *
 * <p>The screen's «&nbsp;Ce mois&nbsp;» button sends {@code dueFrom} and {@code dueTo}, and that
 * call answered 500 on the test server on 17/09/2026 while answering 200 on H2 — the whole suite
 * runs on H2, so nothing caught it. {@code findForSchedule} compares an optional parameter with
 * {@code (:dueFrom is null or i.dueDate >= :dueFrom)}, a shape whose parameter typing PostgreSQL
 * resolves differently from H2. This class pins the behaviour on the engine production uses.</p>
 *
 * <p>The assertions are deliberately about which lines come back rather than about a count of
 * rows: what the promoter needs is that a bounded search returns the instalments inside the
 * window, and only those.</p>
 */
class PostgresInstallmentSearchTest extends AbstractPostgresTest {

    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 1, 15);
    private static final BigDecimal CONTRACT_TOTAL = new BigDecimal("100000.000");

    @Autowired
    private PaymentScheduleService scheduleService;
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

    /**
     * One contract with four quarterly instalments: 15/01, 15/04, 15/07 and 15/10 of 2026.
     */
    @BeforeAll
    void seedAScheduleSpanningTheYear() {
        final ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setCode("PG-SCHED");
        projectRequest.setName("Projet échéancier PostgreSQL");
        projectRequest.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(projectRequest);

        final ClientRequest clientRequest = new ClientRequest();
        clientRequest.setFullName("Acquéreur échéancier");
        clientRequest.setProjectId(this.project.id());
        this.client = this.clientService.create(clientRequest);

        final ClientPurchaseResponse purchase = this.createPurchase();

        final ScheduleTemplateRequest template = new ScheduleTemplateRequest();
        template.setFirstDueDate(FIRST_DUE);
        template.setIntervalMonths(3);
        template.setLines(List.of(line("25"), line("25"), line("25"), line("25")));
        this.scheduleService.generate(purchase.id(), template);
    }

    @Test
    @DisplayName("an unbounded search returns every instalment of the plan")
    void anUnboundedSearchReturnsEveryInstalment() {
        assertThat(this.search(null, null))
                .extracting(PaymentInstallmentResponse::dueDate)
                .containsExactly(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 15),
                        LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("a search bounded on both sides keeps only the instalments inside the window")
    void aSearchBoundedOnBothSidesKeepsOnlyTheWindow() {
        assertThat(this.search(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 31)))
                .extracting(PaymentInstallmentResponse::dueDate)
                .containsExactly(LocalDate.of(2026, 4, 15), LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("a lower bound alone keeps the instalments due on or after it")
    void aLowerBoundAloneKeepsWhatIsDueAfterIt() {
        assertThat(this.search(LocalDate.of(2026, 7, 15), null))
                .extracting(PaymentInstallmentResponse::dueDate)
                .containsExactly(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("an upper bound alone keeps the instalments due on or before it")
    void anUpperBoundAloneKeepsWhatIsDueBeforeIt() {
        assertThat(this.search(null, LocalDate.of(2026, 4, 15)))
                .extracting(PaymentInstallmentResponse::dueDate)
                .containsExactly(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("a window that matches nothing answers an empty list rather than failing")
    void aWindowThatMatchesNothingAnswersEmpty() {
        assertThat(this.search(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31))).isEmpty();
    }

    private List<PaymentInstallmentResponse> search(final LocalDate from, final LocalDate to) {
        return this.scheduleService.search(
                new ListFilter(this.project.id(), null, null, null, null, null, null, from, to, null), null);
    }

    private static ScheduleTemplateRequest.TemplateLine line(final String percentage) {
        final ScheduleTemplateRequest.TemplateLine line = new ScheduleTemplateRequest.TemplateLine();
        line.setPercentage(new BigDecimal(percentage));
        return line;
    }

    private ClientPurchaseResponse createPurchase() {
        final ApartmentRequest apartmentRequest = new ApartmentRequest();
        apartmentRequest.setApartmentNumber("PG-SCHED-1");
        apartmentRequest.setApartmentType("S+2");
        apartmentRequest.setTotalSurface(new BigDecimal("90.000"));
        apartmentRequest.setTotalSalePrice(CONTRACT_TOTAL);
        apartmentRequest.setProjectId(this.project.id());
        apartmentRequest.setAcquirerId(this.client.id());
        final ApartmentResponse apartment = this.apartmentService.create(apartmentRequest);

        final ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setPurchaseDate(LocalDate.of(2026, 1, 5));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(CONTRACT_TOTAL);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setClientId(this.client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartment.id());
        return this.clientPurchaseService.create(request);
    }
}
