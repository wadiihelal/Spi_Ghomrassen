package com.promoteur.app.dto;

import java.math.BigDecimal;

/**
 * What has been contracted and collected on one apartment.
 *
 * <p>An apartment carries at most one sale contract, so these are that contract's figures, or
 * the advances alone when no contract exists yet.</p>
 */
public record ApartmentTotals(
        BigDecimal totalPurchases,
        BigDecimal totalAdvances,
        BigDecimal totalCollected,
        BigDecimal remainingToCollect
) {

    /** Nothing contracted and nothing collected. */
    public static ApartmentTotals empty() {
        return new ApartmentTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
