package com.promoteur.app.repository;

import com.promoteur.app.entity.Apartment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    List<Apartment> findByProjectId(Long projectId);

    Page<Apartment> findByProjectId(Long projectId, Pageable pageable);
}
