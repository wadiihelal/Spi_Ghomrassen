package com.promoteur.app.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a payment schedule as submitted by the console.
 */
@Data
public class InstallmentLineRequest {

    /**
     * What the promoter calls this step, e.g. {@code Tranche 2}.
     */
    @NotBlank
    @Size(max = 120)
    private String label;

    @NotNull
    private LocalDate dueDate;

    @NotNull
    @PositiveOrZero
    private BigDecimal amount;

    private String notes;
}
