package com.roadassist.backend.dto;

import com.roadassist.backend.model.ServiceCategory;

public record ServiceCategoryResponse(Long id, String name, String description, boolean active) {
    public static ServiceCategoryResponse from(ServiceCategory category) {
        return new ServiceCategoryResponse(category.getId(), category.getName(),
            category.getDescription(), category.isActive());
    }
}
