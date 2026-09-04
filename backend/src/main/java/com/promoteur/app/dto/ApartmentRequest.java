package com.promoteur.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload used to create or update an apartment within a project.
 */
@Data
public class ApartmentRequest {

    /**
     * Human-readable apartment number such as A01 or B12.
     */
    @NotBlank
    private String apartmentNumber;

    /**
     * Commercial apartment type such as S+1 or S+2.
     */
    @NotBlank
    private String apartmentType;

    /**
     * Total sellable surface in square meters.
     */
    @NotNull
    @Positive
    private BigDecimal totalSurface;

    /**
     * Optional garden surface in square meters.
     */
    @PositiveOrZero
    private BigDecimal gardenSurface;

    /**
     * Parking reference or free-form parking description attached to the apartment.
     */
    private String parkingCount;

    /**
     * Number of cellars attached to the apartment.
     */
    @PositiveOrZero
    private Integer cellarCount;

    /**
     * Total sale price in Tunisian dinars.
     */
    @PositiveOrZero
    private BigDecimal totalSalePrice;

    /**
     * Optional commercial details shown to end users.
     */
    private String detail;

    /**
     * Building or block the unit belongs to, used to lay out the sales board (UX-05).
     */
    private String block;

    /**
     * Floor, ground floor being zero.
     */
    @PositiveOrZero
    private Integer floorNumber;

    /**
     * Owning project identifier.
     */
    @NotNull
    private Long projectId;

    /**
     * Optional client identifier linked as apartment acquirer.
     */
    private Long acquirerId;
}
