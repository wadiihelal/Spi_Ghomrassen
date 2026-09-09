package com.promoteur.app.apartment;

import com.promoteur.app.client.Client;
import com.promoteur.app.project.Project;
import com.promoteur.app.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

    /**
     * Where the unit stands commercially (UX-05); every apartment starts in stock.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesStatus salesStatus = SalesStatus.AVAILABLE;

    /**
     * Building or block, for the sales board layout.
     */
    @Column(length = 50)
    private String block;

    /**
     * Floor, ground floor being zero.
     */
    private Integer floorNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client acquirer;
}
