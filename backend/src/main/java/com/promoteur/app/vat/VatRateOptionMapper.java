package com.promoteur.app.vat;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VatRateOptionMapper {

    VatRateOptionResponse toResponse(VatRateOption option);
}
