package com.promoteur.app.purchase;

import com.promoteur.app.shared.PurchasePaymentStatus;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ClientPurchaseCalculationServiceImpl implements ClientPurchaseCalculationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Tunisian dinar amounts and the percentages derived from them are kept at scale 3.
     */
    private static final int SCALE = 3;

    @Override
    public PurchaseTotals totals(final BigDecimal totalAmount, final BigDecimal paidAmount,
                                 final BigDecimal advanceAmount) {
        final BigDecimal total = this.normalize(totalAmount);
        final BigDecimal advances = this.normalize(advanceAmount);
        final BigDecimal collected = this.normalize(paidAmount).add(advances);
        final BigDecimal remaining = total.subtract(collected).max(BigDecimal.ZERO);
        final BigDecimal completionPercentage = total.signum() <= 0
                ? BigDecimal.ZERO
                : collected.multiply(ONE_HUNDRED).divide(total, SCALE, RoundingMode.HALF_UP).min(ONE_HUNDRED);
        final PurchasePaymentStatus paymentStatus = this.resolvePaymentStatus(collected, total);

        return new PurchaseTotals(
                advances,
                collected,
                remaining,
                completionPercentage,
                paymentStatus,
                paymentStatus == PurchasePaymentStatus.PAID);
    }

    private PurchasePaymentStatus resolvePaymentStatus(final BigDecimal collectedAmount, final BigDecimal totalAmount) {
        if (collectedAmount.signum() <= 0) {
            return PurchasePaymentStatus.UNPAID;
        }
        if (collectedAmount.compareTo(totalAmount) >= 0) {
            return PurchasePaymentStatus.PAID;
        }
        return PurchasePaymentStatus.PARTIALLY_PAID;
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
