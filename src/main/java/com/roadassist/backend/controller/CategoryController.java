package com.roadassist.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.ServiceCategoryResponse;
import com.roadassist.backend.repository.ServiceCategoryRepository;

@RestController
@RequestMapping("/api/owner/categories")
public class CategoryController {
    private final ServiceCategoryRepository repository;

    public CategoryController(ServiceCategoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ServiceCategoryResponse> activeCategories() {
        return repository.findAllByActiveTrueOrderByNameAsc().stream()
            .map(ServiceCategoryResponse::from).toList();
    }
}
