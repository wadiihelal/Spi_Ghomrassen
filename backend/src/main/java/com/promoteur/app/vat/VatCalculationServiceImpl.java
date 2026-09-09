package com.promoteur.app.vat;

import com.promoteur.app.shared.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class VatCalculationServiceImpl implements VatCalculationService {

    /**
     * Tunisian dinar amounts are counted in millimes.
     */
    private static final int MONEY_SCALE = 3;

    private final MessageService messageService;

    @Override
    public VatAmounts compute(final BigDecimal amountHt, final BigDecimal vatRate) {
        if (amountHt == null) {
            throw new IllegalArgumentException(this.messageService.get("validation.vat.missingAmountHt"));
        }
        if (vatRate == null) {
            throw new IllegalArgumentException(this.messageService.get("validation.vat.missingRate"));
        }
        if (vatRate.signum() < 0) {
            throw new IllegalArgumentException(this.messageService.get("validation.vat.negativeRate", vatRate));
        }

        final BigDecimal net = amountHt.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        final BigDecimal vatAmount = net.multiply(vatRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new VatAmounts(vatAmount, net.add(vatAmount));
    }

    @Override
    public void rejectInconsistentDeclaration(final VatAmounts computed, final BigDecimal declaredVatAmount,
                                              final BigDecimal declaredAmountTtc) {
        if (declaredVatAmount != null && declaredVatAmount.compareTo(computed.vatAmount()) != 0) {
            throw new IllegalArgumentException(this.messageService.get("validation.vat.amountMismatch",
                    declaredVatAmount, computed.vatAmount()));
        }
        if (declaredAmountTtc != null && declaredAmountTtc.compareTo(computed.amountTtc()) != 0) {
            throw new IllegalArgumentException(this.messageService.get("validation.vat.ttcMismatch",
                    declaredAmountTtc, computed.amountTtc()));
        }
    }
}
