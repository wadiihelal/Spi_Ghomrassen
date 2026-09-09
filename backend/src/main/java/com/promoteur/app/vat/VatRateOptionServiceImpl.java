package com.promoteur.app.vat;

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
    private final VatRateOptionMapper vatRateOptionMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<VatRateOptionResponse> findAll(final Pageable pageable) {
        final Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("rate").ascending());
        return this.vatRateOptionRepository.findAll(sorted).map(this.vatRateOptionMapper::toResponse);
    }
}
