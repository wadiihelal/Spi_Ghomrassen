package com.promoteur.app.search;

/**
 * One result of the global search (UX-08).
 *
 * @param type   what was found: CLIENT, APARTMENT, PURCHASE, SUPPLIER or SUPPLIER_INVOICE
 * @param id     identifier of the record
 * @param label  what the user recognises it by: a name, a lot number, a reference
 * @param detail one line of context, e.g. the lot's project or the client's phone
 */
public record SearchHitResponse(String type, Long id, String label, String detail) {
}
