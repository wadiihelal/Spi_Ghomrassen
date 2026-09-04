package com.promoteur.app.mapper;

import com.promoteur.app.dto.InvoiceSettlement;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import com.promoteur.app.entity.SupplierInvoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupplierInvoiceMapper {

    /**
     * @param settlement how far the invoice has been paid, derived by the service
     */
    @Mapping(target = "supplierId", source = "invoice.supplier.id")
    @Mapping(target = "supplierName", source = "invoice.supplier.name")
    @Mapping(target = "projectId", source = "invoice.project.id")
    @Mapping(target = "projectName", source = "invoice.project.name")
    @Mapping(target = "paidAmount", source = "settlement.paidAmount")
    @Mapping(target = "remainingAmount", source = "settlement.remainingAmount")
    @Mapping(target = "status", source = "settlement.status")
    @Mapping(target = "overdue", source = "settlement.overdue")
    @Mapping(target = "daysLate", source = "settlement.daysLate")
    SupplierInvoiceResponse toResponse(SupplierInvoice invoice, InvoiceSettlement settlement);
}
