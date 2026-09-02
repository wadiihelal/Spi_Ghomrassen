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
     * Net amount excluding VAT.
     */
    @NotNull
    @Positive
    private BigDecimal amountHt;

    /**
     * VAT amount.
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal vatAmount;

    /**
     * Gross amount including VAT.
     */
    @NotNull
    @Positive
    private BigDecimal amountTtc;

    /**
     * Retenue a la source amount.
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal withholdingAmount;

    /**
     * Net amount to pay after withholding.
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal netToPay;

    /**
     * Original attachment file name if available.
     */
    private String attachmentName;

    /**
     * Attachment URL stored by the frontend or storage layer.
     */
    private String attachmentUrl;

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
