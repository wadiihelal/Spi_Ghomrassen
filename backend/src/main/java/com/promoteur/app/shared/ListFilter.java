package com.promoteur.app.shared;


import java.time.LocalDate;

/**
 * Optional filters accepted by the list endpoints (PERF-02). Every field is nullable, and a
 * {@code null} or blank value does not restrict the result.
 *
 * <p>Each resource uses the subset that applies to it; the shared shape keeps the controllers
 * and the Angular filter bars symmetrical.</p>
 *
 * @param paymentStatus for sale contracts: UNPAID, PARTIALLY_PAID or PAID. Derived from what
 *                      has been collected, so it is resolved in SQL rather than stored
 * @param search        free text matched against the resource's own labels and those of its
 *                      project, client, supplier or category
 */
public record ListFilter(
        Long projectId,
        Long clientId,
        Long supplierId,
        Long categoryId,
        Long apartmentId,
        String paymentStatus,
        String paymentMethod,
        LocalDate dateFrom,
        LocalDate dateTo,
        String search
) {

    /**
     * Rejects an unknown payment status at the boundary. Left to the specification it would
     * surface as a data-access failure and answer 500 instead of 400.
     */
    public ListFilter {
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            try {
                PurchasePaymentStatus.valueOf(paymentStatus.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Statut de paiement inconnu : " + paymentStatus, ex);
            }
        }
    }

    /**
     * Filter that restricts nothing.
     */
    public static ListFilter none() {
        return new ListFilter(null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Filter restricting to one project, used by the deprecated {@code /by-project/{id}} routes.
     */
    public static ListFilter ofProject(final Long projectId) {
        return new ListFilter(projectId, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Filter restricting to one client, used by the deprecated {@code /by-client/{id}} routes.
     */
    public static ListFilter ofClient(final Long clientId) {
        return new ListFilter(null, clientId, null, null, null, null, null, null, null, null);
    }

    /**
     * Filter restricting to one supplier.
     */
    public static ListFilter ofSupplier(final Long supplierId) {
        return new ListFilter(null, null, supplierId, null, null, null, null, null, null, null);
    }

    /**
     * Filter restricting to one expense category.
     */
    public static ListFilter ofCategory(final Long categoryId) {
        return new ListFilter(null, null, null, categoryId, null, null, null, null, null, null);
    }

    /**
     * Filter restricting to one apartment.
     */
    public static ListFilter ofApartment(final Long apartmentId) {
        return new ListFilter(null, null, null, null, apartmentId, null, null, null, null, null);
    }

    /**
     * @return the requested payment status, or {@code null} when unfiltered
     */
    public PurchasePaymentStatus purchasePaymentStatus() {
        return this.paymentStatus == null || this.paymentStatus.isBlank()
                ? null
                : PurchasePaymentStatus.valueOf(this.paymentStatus.trim());
    }

    /**
     * @return the search term trimmed and lower-cased for a LIKE comparison, or {@code null}
     */
    public String normalizedSearch() {
        return this.search == null || this.search.isBlank() ? null : '%' + this.search.trim().toLowerCase() + '%';
    }
}
