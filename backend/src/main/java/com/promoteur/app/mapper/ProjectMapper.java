package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.entity.Project;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);
}
