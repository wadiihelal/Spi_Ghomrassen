package com.promoteur.app.service;

import com.promoteur.app.dto.report.AmountByLabelDto;
import com.promoteur.app.dto.report.ClientStatementDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Service exposing aggregated dashboard and export reports.
 */
public interface ReportService {

    /**
     * Returns expense totals grouped by category.
     */
    Page<AmountByLabelDto> expensesByCategory(Pageable pageable);

    /**
     * Returns expense totals grouped by project.
     */
    Page<AmountByLabelDto> expensesByProject(Pageable pageable);

    /**
     * Returns paginated client financial statements.
     */
    Page<ClientStatementDto> clientStatements(Pageable pageable);

    /**
     * Returns the financial statement for a single client.
     */
    ClientStatementDto clientStatement(Long clientId);

    /**
     * Returns the global dashboard summary.
     */
    Map<String, Object> globalSummary();

    /**
     * Exports the current reports as an Excel document.
     */
    byte[] exportReportsExcel(Integer year, Integer month);

    /**
     * Exports the current reports as a PDF document.
     */
    byte[] exportReportsPdf(Integer year, Integer month);
}
