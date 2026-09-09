package com.promoteur.app.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierTypeOptionRepository extends JpaRepository<SupplierTypeOption, Long> {

    boolean existsByLabel(String label);
}
