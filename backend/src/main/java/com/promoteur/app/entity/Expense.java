package com.promoteur.app.entity;

import com.promoteur.app.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amountHt;

    /** VAT rate applied, as a fraction: {@code 0.1900} for 19 % (CALC-01). */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @Column(precision = 19, scale = 3)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amountTtc;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod = PaymentMethod.OTHER;

    private String documentNumber;

    private String attachmentName;

    private String attachmentUrl;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
}
