package com.roadassist.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record MechanicProfileUpdateRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Pattern(regexp = "^[0-9+() .-]{7,15}$") String phone,
        @NotBlank @Size(max = 30) String specialization,
        @NotBlank @Size(max = 255) String serviceLocation,
        @NotNull @Min(0) @Max(60) Integer experienceYears,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal mechanicLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal mechanicLongitude) {
    public MechanicProfileUpdateRequest {
        if (fullName != null) fullName = fullName.trim();
        if (phone != null) phone = phone.trim();
        if (specialization != null) specialization = specialization.trim();
        if (serviceLocation != null) serviceLocation = serviceLocation.trim();
    }

    @AssertTrue(message = "Mechanic latitude and longitude must be provided together")
    public boolean isCoordinatePairValid() {
        return (mechanicLatitude == null) == (mechanicLongitude == null);
    }
}
