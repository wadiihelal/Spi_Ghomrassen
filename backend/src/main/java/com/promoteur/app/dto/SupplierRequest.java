package com.promoteur.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
     * Selected supplier type option identifier.
     */
    private Long typeId;

    /**
     * Active flag used by the UI.
     */
    private Boolean active;
}
