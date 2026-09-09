package com.promoteur.app.supplier;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload used to create or update a supplier type option.
 */
@Data
public class SupplierTypeOptionRequest {

    /**
     * Label shown in supplier selection dialogs.
     */
    @NotBlank
    private String label;

    /**
     * Active flag used to keep historical values available.
     */
    private Boolean active;
}
