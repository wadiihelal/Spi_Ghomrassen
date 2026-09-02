package com.promoteur.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @ManyToOne
    @JoinColumn(name = "type_id")
    private SupplierTypeOption type;

    private Boolean active = true;
}
