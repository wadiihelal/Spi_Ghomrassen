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
     * Unique business code for the project.
     */
    @NotBlank
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
