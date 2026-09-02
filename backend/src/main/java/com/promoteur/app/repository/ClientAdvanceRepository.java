package com.promoteur.app.repository;

import com.promoteur.app.entity.ClientAdvance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientAdvanceRepository extends JpaRepository<ClientAdvance, Long> {
    List<ClientAdvance> findByClientId(Long clientId);

    List<ClientAdvance> findByProjectId(Long projectId);

    List<ClientAdvance> findByApartmentId(Long apartmentId);

    Page<ClientAdvance> findByClientId(Long clientId, Pageable pageable);

    Page<ClientAdvance> findByProjectId(Long projectId, Pageable pageable);

    Page<ClientAdvance> findByApartmentId(Long apartmentId, Pageable pageable);

    Optional<ClientAdvance> findByReference(String reference);
}
