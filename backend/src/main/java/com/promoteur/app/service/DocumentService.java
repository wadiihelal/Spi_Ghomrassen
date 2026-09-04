package com.promoteur.app.service;

/**
 * The paper the promoter hands out (UX-06).
 *
 * <p>Three documents, one letterhead: a numbered receipt for a payment collected, a buyer's
 * statement of account, and the month's VAT recap. Each is produced from the stored figures,
 * so nothing on paper can disagree with the screen.</p>
 */
public interface DocumentService {

    /**
     * A receipt for one payment collected, quoting its reference and the amount in words.
     *
     * @param advanceId the payment to receipt
     * @return a print-ready A4 PDF
     */
    byte[] paymentReceipt(Long advanceId);

    /**
     * A buyer's statement: their contracts, everything collected, and what is left to pay.
     *
     * @param clientId the buyer
     * @return a print-ready A4 PDF
     */
    byte[] clientStatement(Long clientId);

    /**
     * The month's deductible VAT, by rate, from expenses and supplier invoices.
     *
     * @param year      calendar year
     * @param month     month, 1 to 12
     * @param projectId project to narrow to, or null for every project
     * @return a print-ready A4 PDF
     */
    byte[] vatSummary(int year, int month, Long projectId);
}
