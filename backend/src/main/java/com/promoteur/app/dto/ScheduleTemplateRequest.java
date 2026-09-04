package com.promoteur.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Generates a schedule from percentages of the contract total, spaced by a fixed interval.
 *
 * <p>The usual off-plan shape: a deposit now, tranches during construction, the balance at
 * handover. The last line absorbs the rounding remainder so the plan always sums to the
 * contract exactly.</p>
 */
@Data
public class ScheduleTemplateRequest {

    /** Due date of the first line; each following line is {@code intervalMonths} later. */
    @NotNull
    private LocalDate firstDueDate;

    @NotNull
    @Positive
    private Integer intervalMonths;

    @NotEmpty
    @Valid
    private List<TemplateLine> lines;

    /** One step of the template. */
    @Data
    public static class TemplateLine {

        @Size(max = 120)
        private String label;

        /** Share of the contract total, in percent. The lines must add up to 100. */
        @NotNull
        @PositiveOrZero
        private BigDecimal percentage;
    }
}
