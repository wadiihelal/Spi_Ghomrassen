package com.promoteur.app.vat;

import java.math.BigDecimal;

public record VatRateOptionResponse(Long id, String label, BigDecimal rate, Boolean active) {
}
