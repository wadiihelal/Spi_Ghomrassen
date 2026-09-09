package com.promoteur.app.purchase;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientPurchaseMapper {

    /**
     * @param purchase the persisted contract
     * @param totals   figures derived by {@code ClientPurchaseCalculationService}
     */
    @Mapping(target = "clientId", source = "purchase.client.id")
    @Mapping(target = "clientName", source = "purchase.client.fullName")
    @Mapping(target = "projectId", source = "purchase.project.id")
    @Mapping(target = "projectName", source = "purchase.project.name")
    @Mapping(target = "apartmentId", source = "purchase.apartment.id")
    @Mapping(target = "apartmentNumber", source = "purchase.apartment.apartmentNumber")
    @Mapping(target = "advanceAmount", source = "totals.advanceAmount")
    @Mapping(target = "collectedAmount", source = "totals.collectedAmount")
    @Mapping(target = "remainingAmount", source = "totals.remainingAmount")
    @Mapping(target = "completionPercentage", source = "totals.completionPercentage")
    @Mapping(target = "paymentStatus", source = "totals.paymentStatus")
    @Mapping(target = "completed", source = "totals.completed")
    ClientPurchaseResponse toResponse(ClientPurchase purchase, PurchaseTotals totals);
}
