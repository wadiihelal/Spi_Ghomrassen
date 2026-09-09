package com.promoteur.app.advance;

import com.promoteur.app.shared.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientAdvanceResponse(
        Long id,
        String reference,
        LocalDate advanceDate,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String attachmentName,
        String attachmentUrl,
        String notes,
        Long clientId,
        String clientName,
        Long projectId,
        String projectName,
        Long apartmentId,
        String apartmentNumber
) {
}
