package com.promoteur.app.vat;

import java.math.BigDecimal;

/**
 * VAT and gross amounts derived from a net amount and a rate, both at scale 3 (millimes).
 *
 * @param vatAmount VAT due
 * @param amountTtc net amount plus VAT
 */
public record VatAmounts(BigDecimal vatAmount, BigDecimal amountTtc) {
}
