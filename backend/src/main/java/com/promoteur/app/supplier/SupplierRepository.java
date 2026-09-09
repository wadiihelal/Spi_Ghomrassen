package com.promoteur.app.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    /**
     * One query for the list endpoint: the associations the response needs are joined.
     */
    @EntityGraph(attributePaths = {"type"})
    @Override
    Page<Supplier> findAll(Pageable pageable);


    /**
     * Global search (UX-08): suppliers are shared, so the whole company is searched.
     */
    @Query("""
            select s from Supplier s
            where lower(s.name) like :pattern or lower(s.fiscalId) like :pattern
            order by s.name
            """)
    List<Supplier> search(@Param("pattern") String pattern, Pageable limit);
}
