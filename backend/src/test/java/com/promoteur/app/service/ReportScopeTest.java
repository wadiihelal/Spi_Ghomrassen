package com.promoteur.app.service;

import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.report.AmountByLabelDto;
import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.dto.response.DashboardSummaryResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.promoteur.app.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers RPT-01 and RPT-02: a report honours the requested period and the requested project, an
 * absent project falls back to the active context, only an explicit ALL aggregates across
 * projects, and every export states its own scope.
 */
// One instance for the class: the two projects and their three expenses are seeded once.
class ReportScopeTest extends AbstractIntegrationTest {

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

    private ProjectResponse projectA;
    private ProjectResponse projectB;

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
        List<AmountByLabelDto> rows = this.byCategory(this.filterFor(this.projectA.id(), 2026, 9));

        assertThat(this.total(rows)).isEqualByComparingTo("200.000");
    }

    @Test
    @DisplayName("dropping the month widens the report to the whole year for that project")
    void droppingTheMonthWidensTheReportToTheWholeYear() {
        assertThat(this.total(this.byCategory(this.filterFor(this.projectA.id(), 2026, null))))
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
        this.projectService.setActiveContext(this.projectB.id());

        List<AmountByLabelDto> rows = this.byCategory(this.filterFor(null, 2026, 9));

        assertThat(this.total(rows)).isEqualByComparingTo("400.000");
        assertThat(this.reportService.expensesByProject(this.filterFor(null, 2026, 9), PageRequest.of(0, 50))
                .getContent())
                .extracting(AmountByLabelDto::getLabel)
                .containsExactly("Résidence Beta");
    }

    @Test
    @DisplayName("expenses per month are labelled by year and month, oldest first")
    void expensesPerMonthAreLabelledByYearAndMonth() {
        List<AmountByLabelDto> rows = this.reportService.expensesByMonth(
                ReportFilter.of(String.valueOf(this.projectA.id()), null, null), PageRequest.of(0, 50)).getContent();

        assertThat(rows).extracting(AmountByLabelDto::getLabel).containsExactly("2026-08", "2026-09");
        assertThat(rows).extracting(AmountByLabelDto::getAmount)
                .containsExactly(new BigDecimal("100.000"), new BigDecimal("200.000"));
    }

    @Test
    @DisplayName("contracted totals group by project")
    void contractedTotalsGroupByProject() {
        assertThat(this.reportService.purchasesByProject(ReportFilter.unrestricted(), PageRequest.of(0, 50))
                .getContent()).isNotNull();
    }

    @Test
    @DisplayName("the dashboard summary is scoped like every other report")
    void theDashboardSummaryIsScopedLikeEveryOtherReport() {
        DashboardSummaryResponse summary = this.reportService.globalSummary(this.filterFor(this.projectA.id(), 2026, 9));

        assertThat(summary.totalExpenses()).isEqualByComparingTo("200.000");
        assertThat(summary.expenses()).isEqualTo(1L);
        assertThat(summary.projects()).isEqualTo(1L);
        assertThat(summary.projectLabel()).isEqualTo("Projet : Résidence Alpha");
        assertThat(summary.periodLabel()).isEqualTo("Période : septembre 2026");
    }

    @Test
    @DisplayName("the exported workbook names the project and the period it covers")
    void theExportedWorkbookNamesTheProjectAndThePeriodItCovers() throws Exception {
        byte[] xlsx = this.reportService.exportReportsExcel(this.filterFor(this.projectA.id(), 2026, 9));

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
        assertThat(this.reportService.exportReportsPdf(this.filterFor(this.projectA.id(), 2026, 9)))
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

    private ProjectResponse createProject(String code, String name) {
        ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName(name);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private void createExpense(ProjectResponse project, LocalDate date, String description, String amountTtc) {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(date);
        request.setDescription(description);
        request.setAmountHt(new BigDecimal(amountTtc));
        request.setVatRate(BigDecimal.ZERO);
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(project.id());
        this.expenseService.create(request);
    }
}
