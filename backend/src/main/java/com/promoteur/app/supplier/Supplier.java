package com.promoteur.app.supplier;

import com.promoteur.app.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "suppliers")
public class Supplier extends BaseEntity {


    @Column(nullable = false)
    private String name;

    private String fiscalId;
    private String phone;
    private String email;
    private String address;

    /**
     * Rate usually invoiced by this supplier, proposed by default at data entry (CALC-01).
     */
    @Column(precision = 5, scale = 4)
    private BigDecimal defaultVatRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private SupplierTypeOption type;

    private Boolean active = true;
}
