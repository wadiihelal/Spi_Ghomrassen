package com.promoteur.app.service;

import com.promoteur.app.entity.VatRateOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read access to the VAT rates offered at data entry.
 */
public interface VatRateOptionService {

    /**
     * Returns the configured VAT rates, ascending.
     */
    Page<VatRateOption> findAll(Pageable pageable);
}
