package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.entity.SupplierInvoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupplierInvoiceMapper {

    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    SupplierInvoiceResponse toResponse(SupplierInvoice invoice);
}
