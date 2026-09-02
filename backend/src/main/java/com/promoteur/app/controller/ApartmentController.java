package com.promoteur.app.controller;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.service.ApartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apartments")
@RequiredArgsConstructor
public class ApartmentController {

    private final ApartmentService apartmentService;

    @GetMapping
    public Page<Apartment> findAll(Pageable pageable) {
        return apartmentService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public Apartment findById(@PathVariable Long id) {
        return apartmentService.findById(id);
    }

    @PostMapping
    public Apartment create(@Valid @RequestBody ApartmentRequest request) {
        return apartmentService.create(request);
    }

    @PutMapping("/{id}")
    public Apartment update(@PathVariable Long id, @Valid @RequestBody ApartmentRequest request) {
        return apartmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        apartmentService.delete(id);
    }

    @GetMapping("/by-project/{projectId}")
    public Page<Apartment> findByProject(@PathVariable Long projectId, Pageable pageable) {
        return apartmentService.findByProject(projectId, pageable);
    }
}
