package com.promoteur.app.repository;

import com.promoteur.app.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByProjectId(Long projectId);
}
