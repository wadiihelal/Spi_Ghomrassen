package com.promoteur.app.service;

import com.promoteur.app.dto.VatAmounts;

import java.math.BigDecimal;

/**
 * Single source of truth for VAT arithmetic (CALC-01). The browser no longer computes money:
 * it sends the net amount and the rate, the backend derives the rest.
 */
public interface VatCalculationService {

    /**
     * Derives the VAT and gross amounts from a net amount and a rate.
     *
     * @param amountHt net amount excluding VAT
     * @param vatRate  rate as a fraction, e.g. {@code 0.1900} for 19 %
     * @return both derived amounts, rounded HALF_UP at scale 3
     */
    VatAmounts compute(BigDecimal amountHt, BigDecimal vatRate);

    /**
     * Guard for hand-crafted payloads: rejects declared amounts that disagree with the derived
     * ones. A payload that omits them is accepted as-is — the derived values are authoritative.
     *
     * @param computed          the amounts derived by {@link #compute(BigDecimal, BigDecimal)}
     * @param declaredVatAmount VAT the caller claimed, may be {@code null}
     * @param declaredAmountTtc gross the caller claimed, may be {@code null}
     * @throws IllegalArgumentException with a French message when a declared amount disagrees
     */
    void rejectInconsistentDeclaration(VatAmounts computed, BigDecimal declaredVatAmount,
                                       BigDecimal declaredAmountTtc);
}
