package com.promoteur.app.invoice;

import com.promoteur.app.project.Project;
import com.promoteur.app.shared.BaseEntity;
import com.promoteur.app.supplier.Supplier;
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

    /**
     * When the supplier expects to be paid. Optional; without it nothing can be late (UX-04).
     */
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amountHt;

    /**
     * VAT rate applied, as a fraction: {@code 0.1900} for 19 % (CALC-01).
     */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amountTtc;

    private String attachmentName;

    private String attachmentUrl;

    @Column(length = 2000)
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;
}
