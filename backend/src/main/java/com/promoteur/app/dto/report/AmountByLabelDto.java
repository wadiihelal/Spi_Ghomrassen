package com.promoteur.app.dto.report;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Generic reporting row representing a monetary amount grouped by label.
 */
@Getter
@AllArgsConstructor
public class AmountByLabelDto {
    private String label;
    private BigDecimal amount;
}
