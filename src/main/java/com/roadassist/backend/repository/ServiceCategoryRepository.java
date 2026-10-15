package com.roadassist.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roadassist.backend.model.ServiceCategory;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    List<ServiceCategory> findAllByOrderByNameAsc();
    List<ServiceCategory> findAllByActiveTrueOrderByNameAsc();
    Optional<ServiceCategory> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndActiveTrue(String name);
}
