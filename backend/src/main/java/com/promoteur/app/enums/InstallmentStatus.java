package com.promoteur.app.enums;

/**
 * State of one instalment, derived from its due date and what has been collected on the
 * contract. Never stored: money received is the only fact, the rest follows from it.
 */
public enum InstallmentStatus {

    /** Fully covered by what the client has paid. */
    PAID,

    /** Partly covered, and not yet past its due date. */
    PARTIALLY_PAID,

    /** Not fully covered and the due date has passed. The one a promoter chases. */
    OVERDUE,

    /** Not yet covered, due date still ahead. */
    UPCOMING
}
