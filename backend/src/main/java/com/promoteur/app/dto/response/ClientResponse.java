package com.promoteur.app.dto.response;

public record ClientResponse(
        Long id,
        String fullName,
        String phone,
        String email,
        String address,
        String cinOrFiscalId,
        String notes,
        Boolean active,
        Long projectId,
        String projectName
) {
}
