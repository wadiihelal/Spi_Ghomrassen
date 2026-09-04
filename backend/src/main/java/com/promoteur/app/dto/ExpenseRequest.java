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
     * VAT rate applied, as a fraction: {@code 0.1900} for 19 %. The backend derives the VAT and
     * gross amounts from this and {@link #amountHt} (CALC-01).
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal vatRate;

    /**
     * VAT amount. Derived by the backend; when present it is only checked for consistency.
     */
    @PositiveOrZero
    private BigDecimal vatAmount;

    /**
     * Gross amount including VAT. Derived by the backend; when present it is only checked for
     * consistency.
     */
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
