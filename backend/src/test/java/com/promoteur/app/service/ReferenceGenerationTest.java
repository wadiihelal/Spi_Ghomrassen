package com.promoteur.app.service;

import com.promoteur.app.AbstractIntegrationTest;
import com.promoteur.app.advance.ClientAdvanceRepository;
import com.promoteur.app.advance.ClientAdvanceRequest;
import com.promoteur.app.advance.ClientAdvanceResponse;
import com.promoteur.app.advance.ClientAdvanceService;
import com.promoteur.app.apartment.ApartmentRequest;
import com.promoteur.app.apartment.ApartmentResponse;
import com.promoteur.app.apartment.ApartmentService;
import com.promoteur.app.client.ClientRequest;
import com.promoteur.app.client.ClientResponse;
import com.promoteur.app.client.ClientService;
import com.promoteur.app.expense.Expense;
import com.promoteur.app.expense.ExpenseCategoryRepository;
import com.promoteur.app.expense.ExpenseRepository;
import com.promoteur.app.expense.ExpenseRequest;
import com.promoteur.app.expense.ExpenseResponse;
import com.promoteur.app.expense.ExpenseService;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectResponse;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers DATA-03: a document reference is allocated before the first save, so no placeholder
 * can ever survive in a column marked unique — not even when the transaction that would have
 * replaced it never commits.
 */
class ReferenceGenerationTest extends AbstractIntegrationTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private ClientAdvanceRepository clientAdvanceRepository;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    private ProjectResponse project;
    private ClientResponse client;

    @BeforeAll
    void seedProjectAndClient() {
        this.project = this.createProject();
        this.client = this.createClient();
    }

    @Test
    @DisplayName("an expense reference carries its year and a sequential number")
    void anExpenseReferenceCarriesItsYearAndASequentialNumber() {
        ExpenseResponse expense = this.createExpense(LocalDate.of(2026, 9, 1));

        assertThat(expense.reference()).matches("DEP-2026-\\d{5}");
    }

    @Test
    @DisplayName("an advance reference carries its year and a sequential number")
    void anAdvanceReferenceCarriesItsYearAndASequentialNumber() {
        ApartmentResponse apartment = this.createApartment();

        assertThat(this.createAdvance(apartment, LocalDate.of(2026, 9, 1)).reference())
                .matches("ACC-2026-\\d{5}");
    }

    @Test
    @DisplayName("references from different years are distinguishable and do not collide")
    void referencesFromDifferentYearsAreDistinguishable() {
        assertThat(this.createExpense(LocalDate.of(2027, 1, 5)).reference()).startsWith("DEP-2027-");
        assertThat(this.createExpense(LocalDate.of(2026, 12, 30)).reference()).startsWith("DEP-2026-");
    }

    @Test
    @DisplayName("a caller's own reference is kept instead of an allocated one")
    void aCallersOwnReferenceIsKept() {
        ExpenseRequest request = this.expenseRequest(LocalDate.of(2026, 9, 1));
        request.setReference("  DEP-MANUEL-1  ");

        assertThat(this.expenseService.create(request).reference()).isEqualTo("DEP-MANUEL-1");
    }

    @Test
    @DisplayName("no reference containing TMP exists after a create")
    void noReferenceContainingTmpExistsAfterACreate() {
        this.createExpense(LocalDate.of(2026, 9, 1));
        this.createAdvance(this.createApartment(), LocalDate.of(2026, 9, 1));

        assertThat(this.expenseRepository.findAll()).extracting(Expense::getReference)
                .noneMatch(reference -> reference.contains("TMP"));
        assertThat(this.clientAdvanceRepository.findAll())
                .extracting(advance -> advance.getReference())
                .noneMatch(reference -> reference.contains("TMP"));
    }

    @Test
    @DisplayName("a create that fails mid-way leaves no reference at all, placeholder or otherwise")
    void aCreateThatFailsMidWayLeavesNoReference() {
        long expensesBefore = this.expenseRepository.count();

        // The category does not exist, so map() throws after the reference was allocated and
        // the whole transaction rolls back.
        ExpenseRequest request = this.expenseRequest(LocalDate.of(2026, 9, 1));
        request.setCategoryId(-1L);

        assertThatThrownBy(() -> this.expenseService.create(request)).isInstanceOf(RuntimeException.class);

        assertThat(this.expenseRepository.count()).isEqualTo(expensesBefore);
        assertThat(this.expenseRepository.findAll()).extracting(Expense::getReference)
                .noneMatch(reference -> reference.contains("TMP"));
    }

    @Test
    @DisplayName("an advance refused by the payment ceiling leaves no reference behind")
    void anAdvanceRefusedByTheCeilingLeavesNoReferenceBehind() {
        long advancesBefore = this.clientAdvanceRepository.count();
        ApartmentResponse apartment = this.createApartment();

        assertThatThrownBy(() -> this.createAdvance(apartment, LocalDate.of(2026, 9, 1), new BigDecimal("999999.000")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(this.clientAdvanceRepository.count()).isEqualTo(advancesBefore);
    }

    private ExpenseResponse createExpense(LocalDate date) {
        return this.expenseService.create(this.expenseRequest(date));
    }

    private ExpenseRequest expenseRequest(LocalDate date) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(date);
        request.setDescription("Dépense référence " + this.sequence.incrementAndGet());
        request.setAmountHt(new BigDecimal("100.000"));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(this.project.id());
        return request;
    }

    private ClientAdvanceResponse createAdvance(ApartmentResponse apartment, LocalDate date) {
        return this.createAdvance(apartment, date, new BigDecimal("1000.000"));
    }

    private ClientAdvanceResponse createAdvance(ApartmentResponse apartment, LocalDate date, BigDecimal amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(date);
        request.setAmount(amount);
        request.setApartmentId(apartment.id());
        return this.clientAdvanceService.create(request);
    }

    private ApartmentResponse createApartment() {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("R-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("90.000"));
        request.setTotalSalePrice(new BigDecimal("200000.000"));
        request.setProjectId(this.project.id());
        request.setAcquirerId(this.client.id());
        return this.apartmentService.create(request);
    }

    @Test
    @DisplayName("a project created without a code receives one from the sequence")
    void aProjectCreatedWithoutACodeReceivesOneFromTheSequence() {
        final ProjectRequest request = new ProjectRequest();
        request.setName("Projet sans code " + this.sequence.incrementAndGet());
        request.setStartDate(LocalDate.of(2026, 4, 1));
        request.setStatus(ProjectStatus.PLANNED);

        final ProjectResponse created = this.projectService.create(request);

        // The year comes from the start date, like every other reference in the application.
        assertThat(created.code()).matches("PRJ-2026-\\d{5}");
    }

    @Test
    @DisplayName("a project code typed by hand is kept exactly as entered")
    void aProjectCodeTypedByHandIsKeptExactlyAsEntered() {
        final ProjectRequest request = new ProjectRequest();
        request.setCode("SPI-GHOM-RES-" + this.sequence.incrementAndGet());
        request.setName("Projet nomenclature maison");
        request.setStatus(ProjectStatus.PLANNED);

        final ProjectResponse created = this.projectService.create(request);

        assertThat(created.code()).isEqualTo(request.getCode());
    }

    @Test
    @DisplayName("two projects created without a code never share one")
    void twoProjectsCreatedWithoutACodeNeverShareOne() {
        final ProjectResponse first = this.createProjectWithoutCode();
        final ProjectResponse second = this.createProjectWithoutCode();

        assertThat(first.code()).isNotEqualTo(second.code());
    }

    @Test
    @DisplayName("updating a project without resending its code keeps the code it already carries")
    void updatingAProjectWithoutItsCodeKeepsTheExistingCode() {
        final ProjectResponse created = this.createProjectWithoutCode();

        final ProjectRequest update = new ProjectRequest();
        update.setName("Projet renommé");
        update.setStatus(ProjectStatus.IN_PROGRESS);
        final ProjectResponse updated = this.projectService.update(created.id(), update);

        assertThat(updated.code()).isEqualTo(created.code());
        assertThat(updated.name()).isEqualTo("Projet renommé");
    }

    private ProjectResponse createProjectWithoutCode() {
        final ProjectRequest request = new ProjectRequest();
        request.setName("Projet auto " + this.sequence.incrementAndGet());
        request.setStartDate(LocalDate.of(2026, 4, 1));
        request.setStatus(ProjectStatus.PLANNED);
        return this.projectService.create(request);
    }

    private ProjectResponse createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("REF-PRJ");
        request.setName("Projet références");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur références");
        request.setProjectId(this.project.id());
        return this.clientService.create(request);
    }
}
