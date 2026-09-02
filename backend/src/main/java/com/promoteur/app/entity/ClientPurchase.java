package com.promoteur.app.entity;

import com.promoteur.app.enums.PurchasePaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "client_purchases")
public class ClientPurchase extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    private LocalDate contractDate;

    @Column(nullable = false)
    private String assetDescription;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal paidAmount = BigDecimal.ZERO;

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
    @JoinColumn(name = "apartment_id", unique = true)
    private Apartment apartment;

    @Transient
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Transient
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    @Transient
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @Transient
    private BigDecimal completionPercentage = BigDecimal.ZERO;

    @Transient
    private PurchasePaymentStatus paymentStatus = PurchasePaymentStatus.UNPAID;

    @Transient
    private Boolean completed = Boolean.FALSE;
}
