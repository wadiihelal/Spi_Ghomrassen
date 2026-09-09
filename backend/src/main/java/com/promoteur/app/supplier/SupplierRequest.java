package com.promoteur.app.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload used to create or update a supplier.
 */
@Data
public class SupplierRequest {

    /**
     * Supplier display name.
     */
    @NotBlank
    private String name;

    /**
     * Fiscal or legal registration identifier.
     */
    private String fiscalId;

    /**
     * Primary phone number.
     */
    private String phone;

    /**
     * Primary email address.
     */
    @Email
    private String email;

    /**
     * Postal address.
     */
    private String address;

    /**
     * Rate usually invoiced by this supplier, as a fraction: {@code 0.1900} for 19 %. Proposed
     * by default when entering an invoice or an expense for this supplier (CALC-01).
     */
    @PositiveOrZero
    private BigDecimal defaultVatRate;

    /**
     * Selected supplier type option identifier.
     */
    private Long typeId;

    /**
     * Active flag used by the UI.
     */
    private Boolean active;
}
