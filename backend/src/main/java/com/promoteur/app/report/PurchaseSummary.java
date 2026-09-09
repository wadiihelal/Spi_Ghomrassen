package com.promoteur.app.report;

import java.math.BigDecimal;

/**
 * Sale contract aggregates for a report scope, computed by the database (PERF-01).
 *
 * @param count             number of contracts
 * @param totalContracted   sum of the contract totals
 * @param totalPaidDirectly sum of the amounts paid directly on the contracts
 */
public record PurchaseSummary(Long count, BigDecimal totalContracted, BigDecimal totalPaidDirectly) {
}
