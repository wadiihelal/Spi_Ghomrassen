package com.promoteur.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierInvoiceResponse(
        Long id,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal amountHt,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal amountTtc,
        String attachmentName,
        String attachmentUrl,
        String detail,
        Long supplierId,
        String supplierName,
        Long projectId,
        String projectName
) {
}
