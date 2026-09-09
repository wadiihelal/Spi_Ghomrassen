package com.promoteur.app.invoice;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A supplier invoice, with how far it has been settled (UX-04).
 *
 * @param dueDate    when the supplier expects payment; may be absent
 * @param paidAmount sum of the payments recorded against it
 * @param status     derived from that sum against the gross amount
 * @param overdue    unsettled and past its due date
 * @param daysLate   days past the due date while unsettled; 0 otherwise
 */
public record SupplierInvoiceResponse(
        Long id,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amountHt,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal amountTtc,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        SettlementStatus status,
        boolean overdue,
        long daysLate,
        String attachmentName,
        String attachmentUrl,
        String detail,
        Long supplierId,
        String supplierName,
        Long projectId,
        String projectName
) {
}
