package com.promoteur.app.report;

import java.time.LocalDate;

/**
 * A {@link ReportFilter} once resolved against the active project context: the concrete project
 * and date bounds the figures cover, plus the French labels the documents print so that every
 * export states its own scope (RPT-01, RPT-02).
 *
 * @param projectId    resolved project identifier, or {@code null} when every project is covered
 * @param projectLabel French label for the project scope
 * @param periodLabel  French label for the period scope
 * @param dateFrom     first date included, or {@code null} when unbounded
 * @param dateTo       last date included, or {@code null} when unbounded
 */
public record ReportScope(Long projectId, String projectLabel, String periodLabel,
                          LocalDate dateFrom, LocalDate dateTo) {

    /**
     * @return the one-line description printed in the Excel header row and the PDF title block
     */
    public String describe() {
        return this.projectLabel + " — " + this.periodLabel;
    }
}
