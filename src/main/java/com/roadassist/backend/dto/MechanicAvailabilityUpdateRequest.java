package com.roadassist.backend.dto;

import com.roadassist.backend.model.User;
import jakarta.validation.constraints.NotNull;

public record MechanicAvailabilityUpdateRequest(
        @NotNull User.Availability availability) {
}
