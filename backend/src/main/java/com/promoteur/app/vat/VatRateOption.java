package com.promoteur.app.vat;

import com.promoteur.app.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A VAT rate the console offers at data entry. Reference data rather than a hard-coded list,
 * so a change in Tunisian law is a row, not a release (CALC-01).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vat_rate_options")
public class VatRateOption extends BaseEntity {

    /**
     * French label shown in the dropdown, e.g. {@code 19 %}.
     */
    @Column(nullable = false)
    private String label;

    /**
     * The rate itself, as a fraction: {@code 0.1900} for 19 %.
     */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    private Boolean active = true;
}
