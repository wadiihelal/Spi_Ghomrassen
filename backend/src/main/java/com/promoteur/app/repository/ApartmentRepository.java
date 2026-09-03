package com.promoteur.app.repository;

import com.promoteur.app.entity.Apartment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    List<Apartment> findByProjectId(Long projectId);

    Page<Apartment> findByProjectId(Long projectId, Pageable pageable);

    /**
     * Loads an apartment holding a write lock on its row, so a payment-ceiling check and the
     * write that follows it are atomic against a concurrent one (CONC-01).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Apartment a where a.id = :id")
    Optional<Apartment> findByIdForUpdate(@Param("id") Long id);
}
