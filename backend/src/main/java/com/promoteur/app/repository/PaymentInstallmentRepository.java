package com.promoteur.app.repository;

import com.promoteur.app.entity.PaymentInstallment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PaymentInstallmentRepository extends JpaRepository<PaymentInstallment, Long> {

    List<PaymentInstallment> findByPurchaseIdOrderBySequenceNoAsc(Long purchaseId);

    List<PaymentInstallment> findByPurchaseIdInOrderByPurchaseIdAscSequenceNoAsc(Collection<Long> purchaseIds);

    void deleteByPurchaseId(Long purchaseId);

    /**
     * Instalments across contracts, for the schedule screen and the dashboard. Every filter is
     * optional. The contract, its client and its apartment are joined because the response
     * names all three.
     */
    @EntityGraph(attributePaths = {"purchase", "purchase.client", "purchase.apartment", "purchase.project"})
    @Query("""
            select i from PaymentInstallment i
            where (:projectId is null or i.purchase.project.id = :projectId)
              and (:clientId is null or i.purchase.client.id = :clientId)
              and (:dueFrom is null or i.dueDate >= :dueFrom)
              and (:dueTo is null or i.dueDate <= :dueTo)
            order by i.dueDate asc, i.purchase.id asc, i.sequenceNo asc
            """)
    List<PaymentInstallment> findForSchedule(@Param("projectId") Long projectId,
                                             @Param("clientId") Long clientId,
                                             @Param("dueFrom") LocalDate dueFrom,
                                             @Param("dueTo") LocalDate dueTo);
}
