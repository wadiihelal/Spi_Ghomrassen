package com.promoteur.app.supplier;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    @Mapping(target = "typeId", source = "type.id")
    @Mapping(target = "typeLabel", source = "type.label")
    SupplierResponse toResponse(Supplier supplier);
}
