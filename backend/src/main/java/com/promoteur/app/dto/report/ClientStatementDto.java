package com.promoteur.app.dto.report;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Aggregated financial statement for a single client.
 *
 * <p>{@code remainingToPay} is derived, never passed in: what a client owes is the total of
 * their contracts minus everything collected, and that formula lives here alone.</p>
 */
@Getter
public class ClientStatementDto {

    private final Long clientId;
    private final String clientName;
    private final BigDecimal totalPurchases;
    private final BigDecimal totalAdvances;
    private final BigDecimal remainingToPay;

    /**
     * @param totalPurchases total of the client's sale contracts
     * @param totalAdvances  everything collected: advances plus payments made on the contracts
     */
    public ClientStatementDto(final Long clientId, final String clientName,
                              final BigDecimal totalPurchases, final BigDecimal totalAdvances) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.totalPurchases = totalPurchases == null ? BigDecimal.ZERO : totalPurchases;
        this.totalAdvances = totalAdvances == null ? BigDecimal.ZERO : totalAdvances;
        this.remainingToPay = this.totalPurchases.subtract(this.totalAdvances);
    }
}
