package com.promoteur.app.dto.response;

import com.promoteur.app.enums.SalesStatus;

import java.math.BigDecimal;

public record ApartmentResponse(
        Long id,
        String apartmentNumber,
        String apartmentType,
        BigDecimal totalSurface,
        BigDecimal gardenSurface,
        String parkingCount,
        Integer cellarCount,
        BigDecimal totalSalePrice,
        String detail,
        /** Commercial state of the unit (UX-05). */
        SalesStatus salesStatus,
        String block,
        Integer floorNumber,
        Long projectId,
        String projectName,
        Long acquirerId,
        String acquirerName,
        /**
         * Figures derived from the apartment's sale contract and the advances collected on it.
         * Carried here so the apartments table can be paginated server-side instead of
         * recomputing them from full expense, purchase and advance lists (PERF-02).
         */
        BigDecimal totalPurchases,
        BigDecimal totalAdvances,
        BigDecimal totalCollected,
        BigDecimal remainingToCollect
) {
}
