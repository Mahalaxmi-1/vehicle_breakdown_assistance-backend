package com.roadassist.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AssistanceRequestCreateRequest {

    @NotNull(message = "Select a vehicle")
    @Positive(message = "Select a valid vehicle")
    private Long vehicleId;

    @NotBlank(message = "Select a problem category")
    @Size(max = 20, message = "Category must be 20 characters or fewer")
    private String problemCategory;

    @NotBlank(message = "Problem description is required")
    @Size(min = 10, max = 1000,
        message = "Problem description must contain 10 to 1000 characters")
    private String description;

    @NotBlank(message = "Current location is required")
    @Size(min = 5, max = 300,
        message = "Location must contain 5 to 300 characters")
    private String location;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getProblemCategory() { return problemCategory; }
    public void setProblemCategory(String problemCategory) {
        this.problemCategory = problemCategory == null ? null : problemCategory.trim();
    }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }
    public String getLocation() { return location; }
    public void setLocation(String location) {
        this.location = location == null ? null : location.trim();
    }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    @AssertTrue(message = "Latitude and longitude must be provided together")
    public boolean isCoordinatePairValid() {
        return (latitude == null) == (longitude == null);
    }
}
