package com.promoteur.app.shared;

import java.time.LocalDate;
import java.util.function.Predicate;

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

    /**
     * Next sale contract reference, e.g. {@code ACH-2026-00042} (UX-09).
     *
     * @param date date the reference year is taken from; today when null
     */
    String nextPurchaseReference(LocalDate date);

    /**
     * Next project code, e.g. {@code PRJ-2026-00042}, for a project created without one.
     *
     * <p>Unlike the three methods above, the caller supplies the «&nbsp;is this one taken&nbsp;»
     * check instead of this service reaching into a feature repository to run it. That is the
     * inversion {@code ArchitectureTest.sharedDoesNotDependOnAFeature} names as the fix for its
     * one exclusion: the three older methods still owe it, this one does not, so {@code shared}
     * gains no new dependency on a feature.</p>
     *
     * @param date         date the code year is taken from; today when null
     * @param alreadyTaken answers whether a candidate code is already used
     */
    String nextProjectCode(LocalDate date, Predicate<String> alreadyTaken);
}
