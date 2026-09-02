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
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier_invoices")
public class SupplierInvoice extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amountHt;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amountTtc;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal withholdingAmount;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal netToPay;

    private String attachmentName;

    private String attachmentUrl;

    @Column(length = 2000)
    private String detail;

    @ManyToOne(optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;
}
