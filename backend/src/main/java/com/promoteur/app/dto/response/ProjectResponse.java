package com.promoteur.app.dto.response;

import com.promoteur.app.enums.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A project as the API returns it. {@code activeContext} is deliberately absent: which project
 * the console has selected is served by {@code GET /api/projects/active-context}, not carried on
 * every row of the list (ARCH-01).
 *
 * @param updatedAt last modification, shown on the project detail screen
 */
public record ProjectResponse(
        Long id,
        String code,
        String name,
        String location,
        String description,
        LocalDate startDate,
        LocalDate expectedEndDate,
        BigDecimal budget,
        ProjectStatus status,
        LocalDateTime updatedAt
) {
}
