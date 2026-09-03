package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.VatRateOptionResponse;
import com.promoteur.app.entity.VatRateOption;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VatRateOptionMapper {

    VatRateOptionResponse toResponse(VatRateOption option);
}
