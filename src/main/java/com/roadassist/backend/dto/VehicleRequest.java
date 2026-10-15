package com.roadassist.backend.dto;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VehicleRequest {

    @NotBlank(message = "Vehicle number is required")
    @Size(max = 15, message = "Vehicle number must not exceed 15 characters")
    @Pattern(regexp = "^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{1,4}$",
        message = "Enter a valid vehicle registration number")
    private String vehicleNumber;

    @NotBlank(message = "Vehicle type is required")
    @Pattern(regexp = "^(Car|Bike|Scooter|Truck|Bus|Other)$",
        message = "Select a valid vehicle type")
    private String vehicleType;

    @NotBlank(message = "Brand is required")
    @Size(max = 50, message = "Brand must not exceed 50 characters")
    private String brand;

    @NotBlank(message = "Model is required")
    @Size(max = 50, message = "Model must not exceed 50 characters")
    private String model;

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber == null ? null
            : vehicleNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType == null ? null : vehicleType.trim();
    }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand == null ? null : brand.trim(); }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null ? null : model.trim(); }
}
