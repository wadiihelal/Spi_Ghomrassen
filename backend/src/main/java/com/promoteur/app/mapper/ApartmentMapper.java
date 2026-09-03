package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.ApartmentResponse;
import com.promoteur.app.entity.Apartment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApartmentMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "acquirerId", source = "acquirer.id")
    @Mapping(target = "acquirerName", source = "acquirer.fullName")
    ApartmentResponse toResponse(Apartment apartment);
}
