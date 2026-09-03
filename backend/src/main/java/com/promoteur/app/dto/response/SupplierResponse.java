package com.promoteur.app.dto.response;

import java.math.BigDecimal;

public record SupplierResponse(
        Long id,
        String name,
        String fiscalId,
        String phone,
        String email,
        String address,
        BigDecimal defaultVatRate,
        Long typeId,
        String typeLabel,
        Boolean active
) {
}
