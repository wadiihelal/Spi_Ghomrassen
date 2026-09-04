package com.promoteur.app.mapper;

import com.promoteur.app.dto.response.AttachmentResponse;
import com.promoteur.app.entity.FileAttachment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttachmentMapper {

    AttachmentResponse toResponse(FileAttachment attachment);
}
