package com.promoteur.app.expense;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload used to create or update an expense category.
 */
@Data
public class ExpenseCategoryRequest {

    /**
     * Category label shown across the finance screens.
     */
    @NotBlank
    private String name;
}
