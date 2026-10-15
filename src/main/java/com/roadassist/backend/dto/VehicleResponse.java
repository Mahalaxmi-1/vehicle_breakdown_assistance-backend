package com.roadassist.backend.dto;

import com.roadassist.backend.model.Vehicle;

public record VehicleResponse(Long id, String vehicleNumber, String vehicleType, String brand, String model) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getVehicleNumber(),
            vehicle.getVehicleType(), vehicle.getBrand(), vehicle.getModel());
    }
}
