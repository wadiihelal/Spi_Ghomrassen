package com.promoteur.app.entity;

import com.promoteur.app.enums.PaymentMethod;
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
import java.time.LocalDate;

/**
 * A payment made against a supplier invoice (UX-04).
 *
 * <p>Several payments may settle one invoice; the invoice's state is read from their sum, so
 * there is no status to keep in step.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier_payments")
public class SupplierPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private SupplierInvoice invoice;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;

    /** Cheque number, transfer reference, whatever the promoter needs to find it again. */
    @Column(length = 120)
    private String reference;

    @Column(length = 1000)
    private String notes;
}
