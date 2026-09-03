package com.promoteur.app.dto.response;

import java.math.BigDecimal;

public record VatRateOptionResponse(Long id, String label, BigDecimal rate, Boolean active) {
}
