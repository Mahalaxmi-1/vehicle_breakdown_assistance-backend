package com.roadassist.backend.dto;

import com.roadassist.backend.model.User;

public record MechanicAvailabilityResponse(User.Availability availability) {
    public static MechanicAvailabilityResponse from(User user) {
        return new MechanicAvailabilityResponse(user.getAvailability());
    }
}
