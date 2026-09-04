package com.promoteur.app.dto.response;

import java.math.BigDecimal;

/**
 * The two figures a promoter looks at first thing: what is late, and what falls due this month.
 *
 * @param overdueCount        instalments past their due date and not settled
 * @param overdueAmount       money owed on them
 * @param dueThisMonthCount   instalments falling due in the current month, still owed
 * @param dueThisMonthAmount  money expected this month
 * @param scheduledAmount     total planned across every contract in scope
 * @param collectedAmount     total received against those plans
 */
public record InstallmentSummaryResponse(
        long overdueCount,
        BigDecimal overdueAmount,
        long dueThisMonthCount,
        BigDecimal dueThisMonthAmount,
        BigDecimal scheduledAmount,
        BigDecimal collectedAmount
) {
}
