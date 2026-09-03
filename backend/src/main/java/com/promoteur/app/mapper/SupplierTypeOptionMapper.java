package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.SupplierTypeOptionResponse;
import com.promoteur.app.entity.SupplierTypeOption;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SupplierTypeOptionMapper {

    SupplierTypeOptionResponse toResponse(SupplierTypeOption type);
}
