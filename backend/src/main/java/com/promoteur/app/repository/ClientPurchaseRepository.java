package com.promoteur.app.repository;

import com.promoteur.app.entity.ClientPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientPurchaseRepository extends JpaRepository<ClientPurchase, Long> {
    List<ClientPurchase> findByClientId(Long clientId);

    List<ClientPurchase> findByProjectId(Long projectId);

    Page<ClientPurchase> findByClientId(Long clientId, Pageable pageable);

    Page<ClientPurchase> findByProjectId(Long projectId, Pageable pageable);

    Page<ClientPurchase> findByApartmentId(Long apartmentId, Pageable pageable);

    Optional<ClientPurchase> findByReference(String reference);

    Optional<ClientPurchase> findByApartmentId(Long apartmentId);
}
