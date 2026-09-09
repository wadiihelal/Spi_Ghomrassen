package com.promoteur.app.apartment;

/**
 * Where one apartment stands commercially (UX-05).
 *
 * <p>{@link #SOLD} follows the sale contract and is set when the contract is recorded.
 * {@link #RESERVED} and {@link #DELIVERED} are decisions the promoter takes, so they are
 * stored: nothing in the data says whether keys have been handed over.</p>
 */
public enum SalesStatus {

    /**
     * Still in stock.
     */
    AVAILABLE,
    /**
     * Held for a buyer, contract not yet signed.
     */
    RESERVED,
    /**
     * Under contract.
     */
    SOLD,
    /**
     * Handed over to its buyer.
     */
    DELIVERED
}
