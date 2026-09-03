package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    ClientResponse toResponse(Client client);
}
