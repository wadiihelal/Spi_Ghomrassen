package com.promoteur.app.repository;

import com.promoteur.app.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    /** One query for the list endpoint: the associations the response needs are joined. */
    @EntityGraph(attributePaths = {"type"})
    @Override
    Page<Supplier> findAll(Pageable pageable);

}
