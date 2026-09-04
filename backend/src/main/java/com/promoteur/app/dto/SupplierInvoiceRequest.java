package com.promoteur.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload used to create or update a supplier invoice.
 */
@Data
public class SupplierInvoiceRequest {

    /**
     * Supplier invoice number.
     */
    @NotBlank
    private String invoiceNumber;

    /**
     * Supplier invoice date.
     */
    @NotNull
    private LocalDate invoiceDate;

    /**
     * When the supplier expects payment. Optional; without it the invoice can never be late.
     */
    private LocalDate dueDate;

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
     * Optional invoice details shown in the UI.
     */
    private String detail;

    /**
     * Supplier identifier.
     */
    @NotNull
    private Long supplierId;

    /**
     * Project identifier owning the invoice.
     */
    @NotNull
    private Long projectId;
}
