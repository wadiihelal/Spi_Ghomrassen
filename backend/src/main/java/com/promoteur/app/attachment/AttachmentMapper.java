package com.promoteur.app.attachment;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttachmentMapper {

    AttachmentResponse toResponse(FileAttachment attachment);
}
