package com.promoteur.app.service.impl;

import com.promoteur.app.entity.VatRateOption;
import com.promoteur.app.repository.VatRateOptionRepository;
import com.promoteur.app.service.VatRateOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VatRateOptionServiceImpl implements VatRateOptionService {

    private final VatRateOptionRepository vatRateOptionRepository;

    @Override
    public Page<VatRateOption> findAll(final Pageable pageable) {
        final Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("rate").ascending());
        return this.vatRateOptionRepository.findAll(sorted);
    }
}
