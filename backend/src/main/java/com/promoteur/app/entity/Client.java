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
@Table(name = "clients")
public class Client extends BaseEntity {


    @Column(nullable = false)
    private String fullName;

    private String phone;
    private String email;
    private String address;
    private String cinOrFiscalId;

    @Column(length = 1000)
    private String notes;

    private Boolean active = true;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;
}
