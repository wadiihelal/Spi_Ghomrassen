package com.promoteur.app;

import com.promoteur.app.expense.ExpenseCategoryRepository;
import com.promoteur.app.project.ProjectRepository;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectResponse;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import com.promoteur.app.shared.ReferenceGeneratorService;
import com.promoteur.app.supplier.SupplierTypeOptionRepository;
import com.promoteur.app.vat.VatRateOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract of {@link DatabaseCleaner}, on which the whole shared-context suite rests.
 *
 * <p>The cleaner has to remove what a test class created while keeping what start-up seeding
 * produced. Get that balance wrong and the failure surfaces far from here: six classes read a
 * category through {@code expenseCategoryRepository.findAll().get(0)} and would throw
 * {@code IndexOutOfBoundsException}, and {@code VatCalculationTest} asserts the exact four
 * seeded VAT rates. Those symptoms name neither the cleaner nor the cause, hence this class.</p>
 */
class DatabaseCleanerTest extends AbstractIntegrationTest {

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private DatabaseCleaner databaseCleaner;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ReferenceGeneratorService referenceGeneratorService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;
    @Autowired
    private SupplierTypeOptionRepository supplierTypeOptionRepository;
    @Autowired
    private VatRateOptionRepository vatRateOptionRepository;

    @Test
    @DisplayName("reference data survives the cleaner so a later class still finds a category")
    void referenceDataSurvivesTheCleaner() {
        this.databaseCleaner.clean();

        assertThat(this.expenseCategoryRepository.findAll()).isNotEmpty();
        assertThat(this.supplierTypeOptionRepository.findAll()).isNotEmpty();
        assertThat(this.vatRateOptionRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("rows created by a test class do not survive the cleaner")
    void rowsCreatedByATestClassDoNotSurviveTheCleaner() {
        this.createProject();
        assertThat(this.projectRepository.findAll()).isNotEmpty();

        this.databaseCleaner.clean();

        assertThat(this.projectRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("the cleaner rewinds the reference sequences so numbering restarts from one")
    void theCleanerRewindsTheReferenceSequences() {
        this.referenceGeneratorService.nextExpenseReference(LocalDate.of(2026, 5, 1));

        this.databaseCleaner.clean();

        assertThat(this.referenceGeneratorService.nextExpenseReference(LocalDate.of(2026, 5, 1)))
                .isEqualTo("DEP-2026-00001");
    }

    private ProjectResponse createProject() {
        final ProjectRequest request = new ProjectRequest();
        request.setCode("CLEAN-" + this.sequence.incrementAndGet());
        request.setName("Projet de nettoyage");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }
}
