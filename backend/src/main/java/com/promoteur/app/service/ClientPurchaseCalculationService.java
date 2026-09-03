package com.promoteur.app.service;

import com.promoteur.app.dto.PurchaseTotals;

import java.math.BigDecimal;

/**
 * Derives the figures the console shows for a sale contract. Pure arithmetic: no persistence,
 * so the rules that decide what a client owes can be reasoned about in one place.
 */
public interface ClientPurchaseCalculationService {

    /**
     * @param totalAmount   contract total
     * @param paidAmount    amount paid directly on the contract
     * @param advanceAmount sum of the advances collected on the apartment
     */
    PurchaseTotals totals(BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal advanceAmount);
}
