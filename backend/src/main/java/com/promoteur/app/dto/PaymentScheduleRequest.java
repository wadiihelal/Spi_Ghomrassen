package com.promoteur.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * A complete payment schedule. Submitting one replaces whatever the contract had: a schedule is
 * a single plan, not a pile of edits.
 */
@Data
public class PaymentScheduleRequest {

    @NotEmpty
    @Valid
    private List<InstallmentLineRequest> lines;
}
