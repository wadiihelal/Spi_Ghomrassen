package com.promoteur.app.vat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Taux de TVA", description = "Référentiel des taux tunisiens (0, 7, 13, 19 %).")
@RestController
@RequestMapping("/api/vat-rates")
@RequiredArgsConstructor
public class VatRateOptionController {

    private final VatRateOptionService vatRateOptionService;

    @Operation(summary = "Liste paginée et filtrée")
    @GetMapping
    public Page<VatRateOptionResponse> findAll(Pageable pageable) {
        return vatRateOptionService.findAll(pageable);
    }
}
