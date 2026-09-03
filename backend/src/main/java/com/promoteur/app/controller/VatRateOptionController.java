package com.promoteur.app.controller;

import com.promoteur.app.entity.VatRateOption;
import com.promoteur.app.service.VatRateOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vat-rates")
@RequiredArgsConstructor
public class VatRateOptionController {

    private final VatRateOptionService vatRateOptionService;

    @GetMapping
    public Page<VatRateOption> findAll(Pageable pageable) {
        return vatRateOptionService.findAll(pageable);
    }
}
