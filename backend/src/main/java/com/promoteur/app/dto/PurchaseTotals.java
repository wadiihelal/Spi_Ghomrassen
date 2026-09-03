package com.promoteur.app.dto;

import com.promoteur.app.enums.PurchasePaymentStatus;

import java.math.BigDecimal;

/**
 * Figures derived from a sale contract and the advances collected against its apartment.
 *
 * <p>Computed on the read path and carried into the response, never written back to the entity
 * (ARCH-01, ARCH-03).</p>
 *
 * @param advanceAmount        sum of the advances on the apartment
 * @param collectedAmount      direct payment plus advances
 * @param remainingAmount      what is still owed, never negative
 * @param completionPercentage share collected, at scale 3, capped at 100
 * @param paymentStatus        UNPAID, PARTIALLY_PAID or PAID
 * @param completed            {@code true} when the contract is fully collected
 */
public record PurchaseTotals(
        BigDecimal advanceAmount,
        BigDecimal collectedAmount,
        BigDecimal remainingAmount,
        BigDecimal completionPercentage,
        PurchasePaymentStatus paymentStatus,
        Boolean completed
) {
}
