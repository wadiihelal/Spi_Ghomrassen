package com.promoteur.app.purchase;

import com.promoteur.app.shared.PurchasePaymentStatus;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A sale contract as the API returns it, including the derived figures the console displays.
 *
 * <p>Those figures used to be six {@code @Transient} fields on the entity, written on the read
 * path purely to shape JSON. They are first-class fields here, and the entity is persistence
 * only (ARCH-01).</p>
 */
public record ClientPurchaseResponse(
        Long id,
        String reference,
        LocalDate purchaseDate,
        LocalDate contractDate,
        String assetDescription,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        String attachmentName,
        String attachmentUrl,
        String notes,
        Long clientId,
        String clientName,
        Long projectId,
        String projectName,
        Long apartmentId,
        String apartmentNumber,
        BigDecimal advanceAmount,
        BigDecimal collectedAmount,
        BigDecimal remainingAmount,
        BigDecimal completionPercentage,
        PurchasePaymentStatus paymentStatus,
        Boolean completed
) {
}
