package com.promoteur.app.document;

import java.math.BigDecimal;

/**
 * Spells a dinar amount out in French, the way a receipt states it (UX-06).
 *
 * <p>A receipt carries the amount twice: in figures and in words. The written form is what
 * settles a dispute over a smudged digit, so it is produced from the same {@code BigDecimal}
 * as the figures and never typed by hand.</p>
 */
public interface AmountInWordsService {

    /**
     * Returns the amount spelled out, dinars and millimes.
     *
     * @param amount amount in dinars, millimes in the third decimal
     * @return e.g. « mille deux cent trente dinars et cinq cents millimes »
     */
    String spell(BigDecimal amount);
}
