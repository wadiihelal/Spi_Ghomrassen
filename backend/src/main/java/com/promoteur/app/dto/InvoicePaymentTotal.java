package com.promoteur.app.dto;

import java.math.BigDecimal;

/**
 * Payments recorded against one supplier invoice, aggregated by the database (UX-04).
 */
public record InvoicePaymentTotal(Long invoiceId, BigDecimal totalAmount) {
}
