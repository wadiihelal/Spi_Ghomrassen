package com.promoteur.app.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload used to create or update a client attached to a project.
 */
@Data
public class ClientRequest {

    /**
     * Full display name of the client.
     */
    @NotBlank
    private String fullName;

    /**
     * Primary phone number.
     */
    private String phone;

    /**
     * Primary email address.
     */
    @Email
    private String email;

    /**
     * Postal address.
     */
    private String address;

    /**
     * CIN or fiscal identifier depending on the client type.
     */
    private String cinOrFiscalId;

    /**
     * Additional CRM notes.
     */
    private String notes;

    /**
     * Active flag used by the UI to hide inactive clients.
     */
    private Boolean active;

    /**
     * Project identifier owning the client.
     */
    @NotNull
    private Long projectId;
}
