package com.promoteur.app.repository;

import com.promoteur.app.dto.VatByRate;
import com.promoteur.app.entity.SupplierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long>, JpaSpecificationExecutor<SupplierInvoice> {
    /**
     * Filtered list endpoint (PERF-02). Redeclared from {@link JpaSpecificationExecutor} so the
     * entity graph applies here as well: filtering must not reintroduce the N+1.
     */
    @EntityGraph(attributePaths = {"supplier", "project"})
    @Override
    Page<SupplierInvoice> findAll(Specification<SupplierInvoice> specification, Pageable pageable);

    /** One query for the list endpoint: the associations the response needs are joined. */
    @EntityGraph(attributePaths = {"supplier", "project"})
    @Override
    Page<SupplierInvoice> findAll(Pageable pageable);

    List<SupplierInvoice> findByProjectId(Long projectId);

    List<SupplierInvoice> findBySupplierId(Long supplierId);

    @EntityGraph(attributePaths = {"supplier", "project"})
    Page<SupplierInvoice> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "project"})
    Page<SupplierInvoice> findBySupplierId(Long supplierId, Pageable pageable);

    /** The same monthly VAT aggregate over supplier invoices (UX-06). */
    @Query("""
            select new com.promoteur.app.dto.VatByRate(i.vatRate, sum(i.amountHt), sum(i.vatAmount))
            from SupplierInvoice i
            where (:projectId is null or i.project.id = :projectId)
              and year(i.invoiceDate) = :year
              and month(i.invoiceDate) = :month
            group by i.vatRate
            order by i.vatRate
            """)
    List<VatByRate> sumVatByRate(@Param("projectId") Long projectId,
                                 @Param("year") int year,
                                 @Param("month") int month);
}
