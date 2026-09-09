package com.promoteur.app.invoice;

/**
 * What the user asked to see in the supplier invoice list (UX-04).
 *
 * <p>Four of the values name a settlement state; {@link #OVERDUE} instead selects whatever is
 * still owed past its due date, whether nothing or only part of it has been paid.</p>
 */
public enum SettlementFilter {

    UNPAID,
    PARTIALLY_PAID,
    PAID,
    /**
     * Still owed, and the due date has passed.
     */
    OVERDUE
}
