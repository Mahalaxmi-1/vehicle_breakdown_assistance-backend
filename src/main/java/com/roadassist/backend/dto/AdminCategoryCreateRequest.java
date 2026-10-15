package com.roadassist.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCategoryCreateRequest(
        @NotBlank @Size(max = 20) String name,
        @NotBlank @Size(max = 150) String description) {
    public AdminCategoryCreateRequest {
        if (name != null) name = name.trim();
        if (description != null) description = description.trim();
    }
}
