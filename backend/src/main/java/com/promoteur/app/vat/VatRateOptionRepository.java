package com.promoteur.app.vat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface VatRateOptionRepository extends JpaRepository<VatRateOption, Long> {

    boolean existsByRate(BigDecimal rate);
}
