package com.promoteur.app.dto;

import com.promoteur.app.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload used to create or update a client advance linked to an apartment.
 */
@Data
public class ClientAdvanceRequest {

    /**
     * Optional custom reference. When omitted the backend generates one.
     */
    private String reference;

    /**
     * Date on which the advance was collected.
     */
    @NotNull
    private LocalDate advanceDate;

    /**
     * Advance amount in Tunisian dinars.
     */
    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * Payment method chosen for the advance.
     */
    private PaymentMethod paymentMethod;

    /**
     * Original file name of the uploaded proof if available.
     */
    private String attachmentName;

    /**
     * Accessible attachment URL if a proof file exists.
     */
    private String attachmentUrl;

    /**
     * Free-form operational notes.
     */
    private String notes;

    /**
     * Linked apartment identifier.
     */
    @NotNull
    private Long apartmentId;
}
