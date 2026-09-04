package com.promoteur.app.dto;

import java.math.BigDecimal;

/**
 * Deductible VAT of one month at one rate (UX-06).
 *
 * @param rate        VAT rate as a fraction, e.g. 0.1900
 * @param baseAmount  total excluding tax the rate applied to
 * @param vatAmount   VAT charged on that base
 */
public record VatByRate(BigDecimal rate, BigDecimal baseAmount, BigDecimal vatAmount) {
}
