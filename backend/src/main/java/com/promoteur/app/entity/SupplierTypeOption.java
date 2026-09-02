package com.promoteur.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier_type_options")
public class SupplierTypeOption extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String label;

    private Boolean active = true;
}
