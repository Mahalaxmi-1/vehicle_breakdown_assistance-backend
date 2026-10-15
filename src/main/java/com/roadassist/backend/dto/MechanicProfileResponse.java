package com.roadassist.backend.dto;

import java.math.BigDecimal;

import com.roadassist.backend.model.User;

public record MechanicProfileResponse(Long id, String fullName, String email, String phone,
        String specialization, String serviceLocation, Integer experienceYears,
        BigDecimal mechanicLatitude, BigDecimal mechanicLongitude, boolean active) {
    public static MechanicProfileResponse from(User user) {
        return new MechanicProfileResponse(user.getId(), user.getFullName(), user.getEmail(),
            user.getPhone(), user.getSpecialization(), user.getServiceLocation(),
            user.getExperienceYears(), user.getMechanicLatitude(), user.getMechanicLongitude(),
            user.isActive());
    }
}
