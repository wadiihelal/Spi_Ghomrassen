package com.promoteur.app.invoice;

import com.promoteur.app.shared.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A payment made against a supplier invoice.
 */
@Data
public class SupplierPaymentRequest {

    @NotNull
    private LocalDate paymentDate;

    @NotNull
    @Positive
    private BigDecimal amount;

    private PaymentMethod paymentMethod;

    /**
     * Cheque number or transfer reference.
     */
    @Size(max = 120)
    private String reference;

    private String notes;
}
