package com.promoteur.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The commercial state of a project's stock (UX-05).
 *
 * @param unitCount      units in scope
 * @param inventoryValue asking price of every unit in scope
 * @param placedValue    asking price of the units reserved, sold or delivered
 * @param availableValue asking price of what is still in stock
 * @param contractedAmount total of the sale contracts signed
 * @param collectedAmount  cash already collected on those contracts
 * @param remainingAmount  what buyers still owe on them
 */
public record SalesBoardResponse(
        long unitCount,
        long availableCount,
        long reservedCount,
        long soldCount,
        long deliveredCount,
        BigDecimal inventoryValue,
        BigDecimal placedValue,
        BigDecimal availableValue,
        BigDecimal contractedAmount,
        BigDecimal collectedAmount,
        BigDecimal remainingAmount,
        List<SalesBoardBlockResponse> blocks
) {
}
