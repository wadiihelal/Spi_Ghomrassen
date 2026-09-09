package com.promoteur.app.advance;

import com.promoteur.app.shared.PaymentMethod;
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
     * Free-form operational notes.
     */
    private String notes;

    /**
     * Linked apartment identifier.
     */
    @NotNull
    private Long apartmentId;
}
