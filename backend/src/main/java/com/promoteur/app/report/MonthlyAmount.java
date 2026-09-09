package com.promoteur.app.report;

import java.math.BigDecimal;

/**
 * A monetary total for one calendar month, as grouped by the database.
 *
 * <p>Year and month come back as separate columns: both PostgreSQL and H2 require every
 * projected expression to appear in {@code GROUP BY}, so the {@code 2026-09} label is built in
 * Java rather than in the query.</p>
 */
public record MonthlyAmount(Integer year, Integer month, BigDecimal amount) {

    /**
     * @return the label the dashboard groups on, e.g. {@code 2026-09}
     */
    public String label() {
        return String.format("%d-%02d", this.year, this.month);
    }
}
