package com.promoteur.app.apartment;

import java.util.List;

/**
 * One floor of a block, with its units in number order (UX-05).
 *
 * @param floorNumber floor index, ground floor being zero; null when the unit carries none
 * @param label       French label such as « RDC » or « 3ᵉ étage »
 */
public record SalesBoardFloorResponse(
        Integer floorNumber,
        String label,
        List<SalesBoardUnitResponse> units
) {
}
