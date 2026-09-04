package com.promoteur.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * A contract's payment schedule and how it stands against the money received.
 *
 * @param scheduledAmount  total of the instalment lines
 * @param unscheduledAmount contract total minus the scheduled amount; zero for a complete plan
 * @param collectedAmount  everything received on the contract, direct payment plus advances
 * @param overdueAmount    sum still owed on lines whose due date has passed
 */
public record PaymentScheduleResponse(
        Long purchaseId,
        String purchaseReference,
        BigDecimal contractAmount,
        BigDecimal scheduledAmount,
        BigDecimal unscheduledAmount,
        BigDecimal collectedAmount,
        BigDecimal overdueAmount,
        List<PaymentInstallmentResponse> installments
) {
}
