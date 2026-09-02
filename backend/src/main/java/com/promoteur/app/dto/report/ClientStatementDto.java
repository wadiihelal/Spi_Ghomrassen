package com.promoteur.app.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Aggregated financial statement for a single client.
 */
@Getter
@Builder
public class ClientStatementDto {
    private Long clientId;
    private String clientName;
    private BigDecimal totalPurchases;
    private BigDecimal totalAdvances;
    private BigDecimal remainingToPay;
}
