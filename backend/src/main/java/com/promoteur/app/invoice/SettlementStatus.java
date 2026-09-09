package com.promoteur.app.invoice;

/**
 * How far a supplier invoice has been settled. Derived from the payments recorded against it,
 * never stored.
 */
public enum SettlementStatus {

    /**
     * Nothing paid yet.
     */
    UNPAID,

    /**
     * Part paid.
     */
    PARTIALLY_PAID,

    /**
     * Settled in full.
     */
    PAID
}
