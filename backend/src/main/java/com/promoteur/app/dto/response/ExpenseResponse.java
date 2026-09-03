package com.promoteur.app.dto.response;

import com.promoteur.app.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        String reference,
        LocalDate expenseDate,
        String description,
        BigDecimal amountHt,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal amountTtc,
        PaymentMethod paymentMethod,
        String documentNumber,
        String attachmentName,
        String attachmentUrl,
        String notes,
        Long categoryId,
        String categoryName,
        Long projectId,
        String projectName,
        Long supplierId,
        String supplierName
) {
}
