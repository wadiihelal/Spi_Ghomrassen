package com.promoteur.app.search;

import com.promoteur.app.apartment.ApartmentRepository;
import com.promoteur.app.client.ClientRepository;
import com.promoteur.app.invoice.SupplierInvoiceRepository;
import com.promoteur.app.purchase.ClientPurchaseRepository;
import com.promoteur.app.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global search (UX-08): five bounded queries, one per type, in the order a promoter thinks —
 * people first, then lots, then paper.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private final ClientRepository clientRepository;
    private final ApartmentRepository apartmentRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;

    /**
     * Two optional fragments on one line, without a dangling separator when one is missing.
     */
    private static String join(final String first, final String second) {
        final boolean hasFirst = first != null && !first.isBlank();
        final boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first + " · " + second;
        }
        return hasFirst ? first : (hasSecond ? second : "");
    }

    @Override
    public List<SearchHitResponse> search(final String query, final Long projectId) {
        final String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        // The wildcards are added here so the JPQL stays a plain `like :q`.
        final String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
        final Pageable limit = PageRequest.of(0, HITS_PER_TYPE);
        final List<SearchHitResponse> hits = new ArrayList<>();

        this.clientRepository.search(pattern, projectId, limit).forEach(client -> hits.add(
                new SearchHitResponse("CLIENT", client.getId(), client.getFullName(),
                        SearchServiceImpl.join(client.getCinOrFiscalId(), client.getPhone()))));

        this.apartmentRepository.search(pattern, projectId, limit).forEach(apartment -> hits.add(
                new SearchHitResponse("APARTMENT", apartment.getId(), apartment.getApartmentNumber(),
                        SearchServiceImpl.join(apartment.getApartmentType(), apartment.getProject().getName()))));

        this.clientPurchaseRepository.search(pattern, projectId, limit).forEach(purchase -> hits.add(
                new SearchHitResponse("PURCHASE", purchase.getId(), purchase.getReference(),
                        SearchServiceImpl.join(purchase.getClient().getFullName(),
                                purchase.getApartment().getApartmentNumber()))));

        this.supplierRepository.search(pattern, limit).forEach(supplier -> hits.add(
                new SearchHitResponse("SUPPLIER", supplier.getId(), supplier.getName(),
                        SearchServiceImpl.join(supplier.getFiscalId(), supplier.getPhone()))));

        this.supplierInvoiceRepository.search(pattern, projectId, limit).forEach(invoice -> hits.add(
                new SearchHitResponse("SUPPLIER_INVOICE", invoice.getId(), invoice.getInvoiceNumber(),
                        SearchServiceImpl.join(invoice.getSupplier().getName(), invoice.getProject().getName()))));

        return hits;
    }
}
