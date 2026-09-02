package com.promoteur.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload used to create or update a client purchase transaction.
 */
@Data
public class ClientPurchaseRequest {

    /**
     * Purchase reference coming from the sales workflow.
     */
    @NotBlank
    private String reference;

    /**
     * Effective purchase date.
     */
    @NotNull
    private LocalDate purchaseDate;

    /**
     * Optional contract signature date.
     */
    private LocalDate contractDate;

    /**
     * Commercial description of the purchased asset.
     */
    @NotBlank
    private String assetDescription;

    /**
     * Total contractual amount in Tunisian dinars.
     */
    @NotNull
    @Positive
    private BigDecimal totalAmount;

    /**
     * Amount already paid directly on the purchase record.
     */
    @PositiveOrZero
    private BigDecimal paidAmount;

    /**
     * Original file name of the supporting attachment.
     */
    private String attachmentName;

    /**
     * Attachment URL stored by the frontend or storage layer.
     */
    private String attachmentUrl;

    /**
     * Additional comments related to the purchase.
     */
    private String notes;

    /**
     * Client identifier owning the purchase.
     */
    @NotNull
    private Long clientId;

    /**
     * Project identifier in which the purchase is recorded.
     */
    @NotNull
    private Long projectId;

    /**
     * Apartment identifier linked to the purchase.
     */
    @NotNull
    private Long apartmentId;
}
