package com.promoteur.app.dashboard;

import java.math.BigDecimal;

/**
 * The dashboard's figures for one report scope (API-02). Replaces the untyped
 * {@code Map<String, Object>} the endpoint used to return.
 *
 * @param projectId                 resolved project, or {@code null} when every project is covered
 * @param projectLabel              French label of the project scope
 * @param periodLabel               French label of the period scope
 * @param clients                   clients in scope (of the project, or all)
 * @param projects                  1 for a project scope, else the project count
 * @param suppliers                 suppliers are not project-scoped; always the global count
 * @param expenses                  expense rows in scope
 * @param clientAdvances            advance rows in scope
 * @param clientPurchases           contract rows in scope
 * @param totalExpenses             sum of expenses TTC
 * @param totalAdvances             everything collected: advances plus direct contract payments
 * @param totalPurchases            sum of contract totals
 * @param totalRemainingFromClients contracts minus everything collected
 */
public record DashboardSummaryResponse(
        Long projectId,
        String projectLabel,
        String periodLabel,
        long clients,
        long projects,
        long suppliers,
        long expenses,
        long clientAdvances,
        long clientPurchases,
        BigDecimal totalExpenses,
        BigDecimal totalAdvances,
        BigDecimal totalPurchases,
        BigDecimal totalRemainingFromClients
) {
}
