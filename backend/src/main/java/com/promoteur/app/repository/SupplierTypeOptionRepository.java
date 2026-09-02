package com.promoteur.app.repository;

import com.promoteur.app.entity.SupplierTypeOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierTypeOptionRepository extends JpaRepository<SupplierTypeOption, Long> {

    boolean existsByLabel(String label);
}
