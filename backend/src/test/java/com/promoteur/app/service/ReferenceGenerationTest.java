package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientAdvanceRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.Expense;
import com.promoteur.app.entity.Project;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_refs;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReferenceGenerationTest {

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

    private Project project;
    private Client client;

    @BeforeAll
    void seedProjectAndClient() {
        this.project = this.createProject();
        this.client = this.createClient();
    }

    @Test
    @DisplayName("an expense reference carries its year and a sequential number")
    void anExpenseReferenceCarriesItsYearAndASequentialNumber() {
        Expense expense = this.createExpense(LocalDate.of(2026, 9, 1));

        assertThat(expense.getReference()).matches("DEP-2026-\\d{5}");
    }

    @Test
    @DisplayName("an advance reference carries its year and a sequential number")
    void anAdvanceReferenceCarriesItsYearAndASequentialNumber() {
        Apartment apartment = this.createApartment();

        assertThat(this.createAdvance(apartment, LocalDate.of(2026, 9, 1)).getReference())
                .matches("ACC-2026-\\d{5}");
    }

    @Test
    @DisplayName("references from different years are distinguishable and do not collide")
    void referencesFromDifferentYearsAreDistinguishable() {
        assertThat(this.createExpense(LocalDate.of(2027, 1, 5)).getReference()).startsWith("DEP-2027-");
        assertThat(this.createExpense(LocalDate.of(2026, 12, 30)).getReference()).startsWith("DEP-2026-");
    }

    @Test
    @DisplayName("a caller's own reference is kept instead of an allocated one")
    void aCallersOwnReferenceIsKept() {
        ExpenseRequest request = this.expenseRequest(LocalDate.of(2026, 9, 1));
        request.setReference("  DEP-MANUEL-1  ");

        assertThat(this.expenseService.create(request).getReference()).isEqualTo("DEP-MANUEL-1");
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
        Apartment apartment = this.createApartment();

        assertThatThrownBy(() -> this.createAdvance(apartment, LocalDate.of(2026, 9, 1), new BigDecimal("999999.000")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(this.clientAdvanceRepository.count()).isEqualTo(advancesBefore);
    }

    private Expense createExpense(LocalDate date) {
        return this.expenseService.create(this.expenseRequest(date));
    }

    private ExpenseRequest expenseRequest(LocalDate date) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(date);
        request.setDescription("Dépense référence " + this.sequence.incrementAndGet());
        request.setAmountHt(new BigDecimal("100.000"));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(this.project.getId());
        return request;
    }

    private com.promoteur.app.entity.ClientAdvance createAdvance(Apartment apartment, LocalDate date) {
        return this.createAdvance(apartment, date, new BigDecimal("1000.000"));
    }

    private com.promoteur.app.entity.ClientAdvance createAdvance(Apartment apartment, LocalDate date, BigDecimal amount) {
        ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(date);
        request.setAmount(amount);
        request.setApartmentId(apartment.getId());
        return this.clientAdvanceService.create(request);
    }

    private Apartment createApartment() {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("R-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("90.000"));
        request.setTotalSalePrice(new BigDecimal("200000.000"));
        request.setProjectId(this.project.getId());
        request.setAcquirerId(this.client.getId());
        return this.apartmentService.create(request);
    }

    private Project createProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("REF-PRJ");
        request.setName("Projet références");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private Client createClient() {
        ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur références");
        request.setProjectId(this.project.getId());
        return this.clientService.create(request);
    }
}
