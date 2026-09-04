package com.promoteur.app.service;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.SupplierPaymentRequest;
import com.promoteur.app.dto.response.PayablesSummaryResponse;
import com.promoteur.app.dto.response.SupplierPaymentResponse;
import com.promoteur.app.enums.SettlementFilter;

import java.util.List;
import com.promoteur.app.dto.SupplierInvoiceRequest;
import com.promoteur.app.dto.response.SupplierInvoiceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing supplier invoice lifecycle operations.
 */
public interface SupplierInvoiceService {

    /**
     * Returns supplier invoices using the requested pagination.
     */
    Page<SupplierInvoiceResponse> findAll(ListFilter filter, Pageable pageable);

    /**
     * Returns a single supplier invoice by identifier.
     */
    SupplierInvoiceResponse findById(Long id);

    /**
     * Creates a new supplier invoice.
     */
    SupplierInvoiceResponse create(SupplierInvoiceRequest request);

    /**
     * Updates an existing supplier invoice.
     */
    SupplierInvoiceResponse update(Long id, SupplierInvoiceRequest request);

    /**
     * Deletes a supplier invoice by identifier.
     */
    void delete(Long id);

    /**
     * Returns invoices for a given project.
     */
    Page<SupplierInvoiceResponse> findByProject(Long projectId, Pageable pageable);

    /**
     * Returns invoices for a given supplier.
     */
    Page<SupplierInvoiceResponse> findBySupplier(Long supplierId, Pageable pageable);

    /**
     * Records a payment against an invoice (UX-04).
     *
     * @throws IllegalArgumentException when the payment would take the invoice past its gross amount
     */
    SupplierPaymentResponse addPayment(Long invoiceId, SupplierPaymentRequest request);

    /**
     * Payments recorded against an invoice, most recent first.
     */
    List<SupplierPaymentResponse> findPayments(Long invoiceId);

    /**
     * Removes one payment. The invoice's state follows from what is left.
     */
    void deletePayment(Long invoiceId, Long paymentId);

    /**
     * What is owed to suppliers in scope, and how much of it is late.
     */
    PayablesSummaryResponse payablesSummary(Long projectId);

    /**
     * Invoices in scope narrowed to one settlement state, or to what is past due.
     */
    org.springframework.data.domain.Page<com.promoteur.app.dto.response.SupplierInvoiceResponse>
        findBySettlement(ListFilter filter, SettlementFilter settlement,
                         org.springframework.data.domain.Pageable pageable);
}
