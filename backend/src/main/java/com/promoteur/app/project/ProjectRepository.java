package com.promoteur.app.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findFirstByActiveContextTrue();

    /**
     * Used to check that a generated project code is free before it is handed out.
     */
    Optional<Project> findByCode(String code);
}
