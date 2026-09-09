package com.promoteur.app.invoice;


import java.math.BigDecimal;

/**
 * How far one supplier invoice has been paid (UX-04). Computed on the read path from the
 * payments recorded against it, never stored.
 */
public record InvoiceSettlement(
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        SettlementStatus status,
        boolean overdue,
        long daysLate
) {

    /**
     * Nothing paid yet on an invoice of {@code amountTtc}.
     */
    public static InvoiceSettlement unpaid(final BigDecimal amountTtc) {
        return new InvoiceSettlement(BigDecimal.ZERO, amountTtc, SettlementStatus.UNPAID, false, 0L);
    }
}
