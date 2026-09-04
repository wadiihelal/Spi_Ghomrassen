package com.promoteur.app.dto.response;

import java.math.BigDecimal;

/**
 * What the promoter owes suppliers, for the invoices screen and the dashboard (UX-04).
 *
 * @param invoicedAmount total billed by suppliers in scope
 * @param paidAmount     total already settled
 * @param dueAmount      still owed
 * @param overdueCount   unsettled invoices past their due date
 * @param overdueAmount  money owed on them
 */
public record PayablesSummaryResponse(
        long invoiceCount,
        BigDecimal invoicedAmount,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        long overdueCount,
        BigDecimal overdueAmount
) {
}
