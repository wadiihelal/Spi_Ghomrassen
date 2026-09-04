package com.promoteur.app.dto.response;

import com.promoteur.app.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierPaymentResponse(
        Long id,
        Long invoiceId,
        String invoiceNumber,
        LocalDate paymentDate,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String reference,
        String notes
) {
}
