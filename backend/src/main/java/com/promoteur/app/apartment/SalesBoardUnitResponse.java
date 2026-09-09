package com.promoteur.app.apartment;


import java.math.BigDecimal;

/**
 * One apartment as it appears on the sales board (UX-05).
 */
public record SalesBoardUnitResponse(
        Long id,
        String apartmentNumber,
        String apartmentType,
        BigDecimal totalSurface,
        BigDecimal totalSalePrice,
        SalesStatus salesStatus,
        String acquirerName,
        /** Contract amount, zero while the unit is not under contract. */
        BigDecimal contractedAmount,
        BigDecimal collectedAmount,
        BigDecimal remainingAmount
) {
}
