package com.promoteur.app.report;

import java.math.BigDecimal;

/**
 * A row count and a monetary total for one resource inside a report scope, aggregated by the
 * database rather than by loading every row (PERF-01).
 */
public record CountAndTotal(Long count, BigDecimal total) {
}
