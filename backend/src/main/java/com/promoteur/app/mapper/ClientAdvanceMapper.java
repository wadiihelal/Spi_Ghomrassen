package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.ClientAdvanceResponse;
import com.promoteur.app.entity.ClientAdvance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientAdvanceMapper {

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.fullName")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "apartmentId", source = "apartment.id")
    @Mapping(target = "apartmentNumber", source = "apartment.apartmentNumber")
    ClientAdvanceResponse toResponse(ClientAdvance advance);
}
