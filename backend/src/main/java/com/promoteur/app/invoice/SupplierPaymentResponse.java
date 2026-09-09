package com.promoteur.app.invoice;

import com.promoteur.app.shared.PaymentMethod;

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
