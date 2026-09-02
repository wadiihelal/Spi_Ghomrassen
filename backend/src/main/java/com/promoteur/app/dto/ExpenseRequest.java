package com.promoteur.app.dto;

import com.promoteur.app.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload used to create or update an expense entry.
 */
@Data
public class ExpenseRequest {

    /**
     * Optional custom reference. When absent the backend generates one.
     */
    private String reference;

    /**
     * Accounting date of the expense.
     */
    @NotNull
    private LocalDate expenseDate;

    /**
     * Business description shown in tables and exports.
     */
    @NotBlank
    private String description;

    /**
     * Net amount excluding VAT.
     */
    @NotNull
    @Positive
    private BigDecimal amountHt;

    /**
     * VAT amount associated with the expense.
     */
    @PositiveOrZero
    private BigDecimal vatAmount;

    /**
     * Gross amount including VAT.
     */
    @NotNull
    @Positive
    private BigDecimal amountTtc;

    /**
     * Payment method used for settlement.
     */
    private PaymentMethod paymentMethod;

    /**
     * Supplier document or invoice number.
     */
    private String documentNumber;

    /**
     * Original file name for the supporting document.
     */
    private String attachmentName;

    /**
     * Attachment URL stored by the frontend or storage layer.
     */
    private String attachmentUrl;

    /**
     * Free-form operational notes.
     */
    private String notes;

    /**
     * Expense category identifier.
     */
    @NotNull
    private Long categoryId;

    /**
     * Project identifier owning the expense.
     */
    @NotNull
    private Long projectId;

    /**
     * Optional supplier identifier.
     */
    private Long supplierId;
}
