package com.promoteur.app.service;

import java.time.LocalDate;

/**
 * Allocates the sequential document references shown in the console and in exports (DATA-03).
 *
 * <p>A reference is issued in one call, before the row is first saved, so no placeholder ever
 * reaches the database.</p>
 */
public interface ReferenceGeneratorService {

    /**
     * @param date accounting date of the expense, whose year appears in the reference
     * @return the next unused expense reference, e.g. {@code DEP-2026-00042}
     */
    String nextExpenseReference(LocalDate date);

    /**
     * @param date date of the advance, whose year appears in the reference
     * @return the next unused advance reference, e.g. {@code ACC-2026-00042}
     */
    String nextAdvanceReference(LocalDate date);
}
