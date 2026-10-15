package com.roadassist.backend.dto;

import java.time.LocalDateTime;

import com.roadassist.backend.model.User;

public record AdminUserResponse(Long id, String name, String email, String phone,
        String role, boolean active, String availability, String specialization,
        String serviceLocation, LocalDateTime createdAt) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(),
            user.getPhone(), user.getRole().name(), user.isActive(),
            user.getAvailability() == null ? null : user.getAvailability().name(),
            user.getSpecialization(), user.getServiceLocation(), user.getCreatedAt());
    }
}
