package com.promoteur.app.service;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.report.AmountByLabelDto;
import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.entity.Project;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers RPT-01 and RPT-02: a report honours the requested period and the requested project, an
 * absent project falls back to the active context, only an explicit ALL aggregates across
 * projects, and every export states its own scope.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_reports;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
// One instance for the class: the two projects and their three expenses are seeded once.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportScopeTest {

    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 15);
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 15);

    @Autowired
    private ReportService reportService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    private Project projectA;
    private Project projectB;

    @BeforeAll
    void seedTwoProjectsAcrossTwoMonths() {
        this.projectA = this.createProject("RPT-A", "Résidence Alpha");
        this.projectB = this.createProject("RPT-B", "Résidence Beta");

        this.createExpense(this.projectA, AUGUST, "Alpha août", "100.000");
        this.createExpense(this.projectA, SEPTEMBER, "Alpha septembre", "200.000");
        this.createExpense(this.projectB, SEPTEMBER, "Beta septembre", "400.000");
    }

    @Test
    @DisplayName("a report for one project and one month excludes other projects and other months")
    void aReportForOneProjectAndOneMonthExcludesOtherProjectsAndOtherMonths() {
        List<AmountByLabelDto> rows = this.byCategory(this.filterFor(this.projectA.getId(), 2026, 9));

        assertThat(this.total(rows)).isEqualByComparingTo("200.000");
    }

    @Test
    @DisplayName("dropping the month widens the report to the whole year for that project")
    void droppingTheMonthWidensTheReportToTheWholeYear() {
        assertThat(this.total(this.byCategory(this.filterFor(this.projectA.getId(), 2026, null))))
                .isEqualByComparingTo("300.000");
    }

    @Test
    @DisplayName("only an explicit projectId=ALL aggregates across projects")
    void onlyAnExplicitAllAggregatesAcrossProjects() {
        ReportFilter all = ReportFilter.of(ReportFilter.ALL_PROJECTS, 2026, 9);

        assertThat(this.total(this.byCategory(all))).isEqualByComparingTo("600.000");
        assertThat(this.reportService.expensesByProject(all, PageRequest.of(0, 50)).getContent())
                .extracting(AmountByLabelDto::getLabel)
                .containsExactlyInAnyOrder("Résidence Alpha", "Résidence Beta");
    }

    @Test
    @DisplayName("an absent projectId falls back to the active project context")
    void anAbsentProjectIdFallsBackToTheActiveProjectContext() {
        this.projectService.setActiveContext(this.projectB.getId());

        List<AmountByLabelDto> rows = this.byCategory(this.filterFor(null, 2026, 9));

        assertThat(this.total(rows)).isEqualByComparingTo("400.000");
        assertThat(this.reportService.expensesByProject(this.filterFor(null, 2026, 9), PageRequest.of(0, 50))
                .getContent())
                .extracting(AmountByLabelDto::getLabel)
                .containsExactly("Résidence Beta");
    }

    @Test
    @DisplayName("the dashboard summary is scoped like every other report")
    void theDashboardSummaryIsScopedLikeEveryOtherReport() {
        Map<String, Object> summary = this.reportService.globalSummary(this.filterFor(this.projectA.getId(), 2026, 9));

        assertThat(summary.get("totalExpenses")).isEqualTo(new BigDecimal("200.000"));
        assertThat(summary.get("expenses")).isEqualTo(1L);
        assertThat(summary.get("projects")).isEqualTo(1L);
        assertThat(summary.get("projectLabel")).isEqualTo("Projet : Résidence Alpha");
        assertThat(summary.get("periodLabel")).isEqualTo("Période : septembre 2026");
    }

    @Test
    @DisplayName("the exported workbook names the project and the period it covers")
    void theExportedWorkbookNamesTheProjectAndThePeriodItCovers() throws Exception {
        byte[] xlsx = this.reportService.exportReportsExcel(this.filterFor(this.projectA.getId(), 2026, 9));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Dépenses par catégorie");

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Projet : Résidence Alpha — Période : septembre 2026");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Catégorie");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(200.0);
            assertThat(sheet.getRow(3)).as("only one category row for this scope").isNull();
        }
    }

    @Test
    @DisplayName("the exported PDF is produced for the requested scope")
    void theExportedPdfIsProducedForTheRequestedScope() {
        assertThat(this.reportService.exportReportsPdf(this.filterFor(this.projectA.getId(), 2026, 9)))
                .isNotEmpty();
    }

    private ReportFilter filterFor(Long projectId, Integer year, Integer month) {
        return ReportFilter.of(projectId == null ? null : String.valueOf(projectId), year, month);
    }

    private List<AmountByLabelDto> byCategory(ReportFilter filter) {
        return this.reportService.expensesByCategory(filter, PageRequest.of(0, 50)).getContent();
    }

    private BigDecimal total(List<AmountByLabelDto> rows) {
        return rows.stream().map(AmountByLabelDto::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Project createProject(String code, String name) {
        ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName(name);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private void createExpense(Project project, LocalDate date, String description, String amountTtc) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(date);
        request.setDescription(description);
        request.setAmountHt(new BigDecimal(amountTtc));
        request.setVatAmount(BigDecimal.ZERO);
        request.setAmountTtc(new BigDecimal(amountTtc));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(project.getId());
        this.expenseService.create(request);
    }
}
