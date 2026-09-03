package com.promoteur.app.mapper;

import com.promoteur.app.dto.ApartmentTotals;
import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.entity.Apartment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApartmentMapper {

    /**
     * @param totals figures derived from the apartment's contract and advances
     */
    @Mapping(target = "projectId", source = "apartment.project.id")
    @Mapping(target = "projectName", source = "apartment.project.name")
    @Mapping(target = "acquirerId", source = "apartment.acquirer.id")
    @Mapping(target = "acquirerName", source = "apartment.acquirer.fullName")
    @Mapping(target = "totalPurchases", source = "totals.totalPurchases")
    @Mapping(target = "totalAdvances", source = "totals.totalAdvances")
    @Mapping(target = "totalCollected", source = "totals.totalCollected")
    @Mapping(target = "remainingToCollect", source = "totals.remainingToCollect")
    ApartmentResponse toResponse(Apartment apartment, ApartmentTotals totals);
}
