package com.promoteur.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "apartments")
public class Apartment extends BaseEntity {

    @Column(nullable = false)
    private String apartmentNumber;

    @Column(nullable = false)
    private String apartmentType;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal totalSurface;

    @Column(precision = 19, scale = 3)
    private BigDecimal gardenSurface;

    private String parkingCount;

    private Integer cellarCount;

    @Column(precision = 19, scale = 3)
    private BigDecimal totalSalePrice;

    @Column(length = 2000)
    private String detail;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client acquirer;
}
