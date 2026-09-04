package com.promoteur.app.service;

import com.promoteur.app.dto.report.AmountByLabelDto;
import com.promoteur.app.dto.report.ClientStatementDto;
import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.dto.report.ReportScope;
import com.promoteur.app.dto.response.DashboardSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


/**
 * Service exposing aggregated dashboard and export reports.
 *
 * <p>Every method is scoped by a {@link ReportFilter}. A filter with no project resolves to the
 * active project context; only an explicit {@code projectId=ALL} aggregates across projects
 * (RPT-02). A filter with a year, optionally a month, restricts the figures to that period
 * (RPT-01).</p>
 */
public interface ReportService {

    /**
     * Resolves a filter against the active project context and the calendar.
     *
     * @return the concrete project and date bounds the figures will cover, with French labels
     */
    ReportScope resolveScope(ReportFilter filter);

    /**
     * Returns expense totals grouped by category, inside the filter's scope.
     */
    Page<AmountByLabelDto> expensesByCategory(ReportFilter filter, Pageable pageable);

    /**
     * Returns expense totals grouped by project, inside the filter's scope.
     */
    Page<AmountByLabelDto> expensesByProject(ReportFilter filter, Pageable pageable);

    /**
     * Returns expense totals per calendar month, inside the filter's scope, oldest first.
     */
    Page<AmountByLabelDto> expensesByMonth(ReportFilter filter, Pageable pageable);

    /**
     * Returns contracted totals grouped by project, inside the filter's scope.
     */
    Page<AmountByLabelDto> purchasesByProject(ReportFilter filter, Pageable pageable);

    /**
     * Returns advance totals grouped by payment method, inside the filter's scope.
     */
    Page<AmountByLabelDto> advancesByPaymentMethod(ReportFilter filter, Pageable pageable);

    /**
     * Returns paginated client financial statements, inside the filter's scope.
     */
    Page<ClientStatementDto> clientStatements(ReportFilter filter, Pageable pageable);

    /**
     * Returns the financial statement for a single client, inside the filter's scope.
     */
    ClientStatementDto clientStatement(Long clientId, ReportFilter filter);

    /**
     * Returns the dashboard summary, inside the filter's scope.
     */
    DashboardSummaryResponse globalSummary(ReportFilter filter);

    /**
     * Exports the reports for the filter's scope as an Excel document. The workbook states its
     * own project and period in the first row of every sheet.
     */
    byte[] exportReportsExcel(ReportFilter filter);

    /**
     * Exports the reports for the filter's scope as a PDF document. The title block states the
     * project and the period.
     */
    byte[] exportReportsPdf(ReportFilter filter);
}
