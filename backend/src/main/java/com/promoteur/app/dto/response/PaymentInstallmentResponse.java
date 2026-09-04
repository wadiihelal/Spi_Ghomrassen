package com.promoteur.app.dto.response;

import com.promoteur.app.enums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One instalment, with what has been settled against it and how late it is.
 *
 * @param settledAmount   share of the money collected on the contract that this line absorbs
 * @param remainingAmount what is still owed on this line, never negative
 * @param status          derived from the due date and the settled amount
 * @param daysLate        days past the due date while unsettled; 0 when on time or paid
 */
public record PaymentInstallmentResponse(
        Long id,
        Long purchaseId,
        String purchaseReference,
        Long clientId,
        String clientName,
        Long projectId,
        String projectName,
        Long apartmentId,
        String apartmentNumber,
        Integer sequenceNo,
        String label,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        InstallmentStatus status,
        long daysLate,
        String notes
) {
}
