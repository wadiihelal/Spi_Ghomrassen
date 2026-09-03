package com.promoteur.app.dto.response;

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
        Long projectId,
        String projectName,
        Long acquirerId,
        String acquirerName
) {
}
