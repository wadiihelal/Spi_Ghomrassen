package com.promoteur.app.supplier;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SupplierTypeOptionMapper {

    SupplierTypeOptionResponse toResponse(SupplierTypeOption type);
}
