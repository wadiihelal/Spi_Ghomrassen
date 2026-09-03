package com.promoteur.app.service;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.AuditLogResponse;
import com.promoteur.app.dto.response.ExpenseResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the audit trail contract from SEC-03 and I18N-01: every write records who acted, the
 * summary of a deletion is built from values captured before the row left the database, and the
 * French labels come from {@code messages_fr.properties} correctly accented.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class AuditTrailTest {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Test
    @DisplayName("creating an expense records who acted, in accented French")
    void creatingAnExpenseRecordsWhoActedInAccentedFrench() {
        ExpenseResponse expense = this.createExpense("Frais de dossier baladiya");

        AuditLogResponse entry = this.lastEntryFor("EXPENSE", expense.id());
        assertThat(entry.action()).isEqualTo("CREATE");
        assertThat(entry.actor()).isEqualTo("system");
        assertThat(entry.summary()).isEqualTo("Dépense Frais de dossier baladiya enregistrée.");
    }

    @Test
    @DisplayName("deleting an expense describes the row that was removed, not a detached entity")
    void deletingAnExpenseDescribesTheRowThatWasRemoved() {
        ExpenseResponse expense = this.createExpense("Honoraires notaire");
        Long expenseId = expense.id();

        this.expenseService.delete(expenseId);

        AuditLogResponse entry = this.lastEntryFor("EXPENSE", expenseId);
        assertThat(entry.action()).isEqualTo("DELETE");
        assertThat(entry.summary()).isEqualTo("Dépense Honoraires notaire supprimée.");
    }

    @Test
    @DisplayName("deleting a project names the project even though the row is already gone")
    void deletingAProjectNamesTheProjectEvenThoughTheRowIsAlreadyGone() {
        ProjectResponse project = this.createProject("AUDIT-DEL", "Résidence à supprimer");
        Long projectId = project.id();

        this.projectService.delete(projectId);

        AuditLogResponse entry = this.lastEntryFor("PROJECT", projectId);
        assertThat(entry.action()).isEqualTo("DELETE");
        assertThat(entry.summary()).isEqualTo("Projet Résidence à supprimer supprimé.");
    }

    @Test
    @DisplayName("the audit log can be filtered by entity type, by actor and by date")
    void theAuditLogCanBeFilteredByEntityTypeByActorAndByDate() {
        this.createExpense("Dépense filtrée");

        assertThat(this.auditLogService.search("EXPENSE", null, null, null, PageRequest.of(0, 50)).getContent())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.entityType()).isEqualTo("EXPENSE"));

        assertThat(this.auditLogService.search(null, "system", null, null, PageRequest.of(0, 50)).getContent())
                .isNotEmpty();

        assertThat(this.auditLogService.search(null, "someone-else", null, null, PageRequest.of(0, 50)).getContent())
                .isEmpty();

        assertThat(this.auditLogService.search(null, null, LocalDate.now(), LocalDate.now(), PageRequest.of(0, 50))
                .getContent()).isNotEmpty();

        assertThat(this.auditLogService.search(null, null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(4),
                PageRequest.of(0, 50)).getContent()).isEmpty();
    }

    private ExpenseResponse createExpense(String description) {
        ProjectResponse project = this.createProject("AUDIT-" + description.hashCode(), "Projet audit");

        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(LocalDate.now());
        request.setDescription(description);
        request.setAmountHt(new BigDecimal("1000.000"));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(project.id());
        return this.expenseService.create(request);
    }

    private ProjectResponse createProject(String code, String name) {
        ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName(name);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private AuditLogResponse lastEntryFor(String entityType, Long entityId) {
        Page<AuditLogResponse> page = this.auditLogService.findByEntity(entityType, entityId, PageRequest.of(0, 10));
        List<AuditLogResponse> entries = page.getContent();
        assertThat(entries).as("audit entries for %s %s", entityType, entityId).isNotEmpty();
        return entries.get(0);
    }
}
