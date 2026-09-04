package com.promoteur.app.dto.response;

import java.util.List;

/**
 * One block of the project, with its floors from the top down (UX-05).
 */
public record SalesBoardBlockResponse(
        String block,
        int unitCount,
        int availableCount,
        List<SalesBoardFloorResponse> floors
) {
}
