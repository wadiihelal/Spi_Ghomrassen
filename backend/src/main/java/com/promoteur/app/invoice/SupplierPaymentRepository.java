package com.promoteur.app.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findByInvoiceIdOrderByPaymentDateDesc(Long invoiceId);

    /**
     * Payment totals for a set of invoices, in one query, so a page of invoices can be enriched
     * without a lookup per row.
     */
    @Query("""
            select new com.promoteur.app.invoice.InvoicePaymentTotal(p.invoice.id, sum(p.amount))
            from SupplierPayment p
            where p.invoice.id in :invoiceIds
            group by p.invoice.id
            """)
    List<InvoicePaymentTotal> sumByInvoiceIds(@Param("invoiceIds") Collection<Long> invoiceIds);
}
