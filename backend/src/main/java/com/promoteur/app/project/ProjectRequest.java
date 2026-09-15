package com.promoteur.app.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload used to create or update a project.
 */
@Data
public class ProjectRequest {

    /**
     * Unique business code for the project. Optional since 15/09/2026: left blank, the server
     * allocates {@code PRJ-2026-00042} from a sequence, exactly as it does for expenses, advances
     * and sale contracts. A code that is supplied is kept as entered.
     */
    private String code;

    /**
     * Display name used throughout the application.
     */
    @NotBlank
    private String name;

    /**
     * Physical project location.
     */
    private String location;

    /**
     * Longer commercial or operational description.
     */
    private String description;

    /**
     * Planned start date.
     */
    private LocalDate startDate;

    /**
     * Planned end date.
     */
    private LocalDate expectedEndDate;

    /**
     * Optional project budget in Tunisian dinars.
     */
    @PositiveOrZero
    private BigDecimal budget;

    /**
     * Project lifecycle status.
     */
    private ProjectStatus status;
}
