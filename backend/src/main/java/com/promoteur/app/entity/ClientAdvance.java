package com.promoteur.app.entity;

import com.promoteur.app.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "client_advances")
public class ClientAdvance extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private LocalDate advanceDate;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod = PaymentMethod.OTHER;

    private String attachmentName;

    private String attachmentUrl;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(optional = false)
    @JoinColumn(name = "apartment_id")
    private Apartment apartment;
}
