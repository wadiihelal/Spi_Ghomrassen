package com.promoteur.app.apartment;

import java.math.BigDecimal;

/**
 * Advances collected on one apartment, aggregated by the database.
 *
 * <p>Lets a page of sale contracts resolve its advance totals in one query instead of one per
 * row (PERF-03).</p>
 */
public record ApartmentAdvanceTotal(Long apartmentId, BigDecimal totalAmount) {
}
