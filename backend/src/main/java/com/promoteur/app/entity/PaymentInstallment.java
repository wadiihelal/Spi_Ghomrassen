package com.promoteur.app.entity;

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
import java.time.LocalDate;

/**
 * One line of a sale contract's payment schedule: a label, a date, an amount.
 *
 * <p>Deliberately carries no status and no link to the payments that settle it. What the client
 * has actually paid is spread across the schedule in sequence order at read time, so the plan
 * and the cash can never disagree.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_installments")
public class PaymentInstallment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id")
    private ClientPurchase purchase;

    /** Position in the plan, starting at 1. Payments settle instalments in this order. */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    /** What the promoter calls this step: {@code Réservation}, {@code Tranche 2}, {@code Livraison}. */
    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Column(length = 1000)
    private String notes;
}
