package com.roadassist.backend.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import com.roadassist.backend.model.AssistanceRequest;

public record MechanicRequestResponse(
        Long id,
        Long vehicleId,
        String vehicleNumber,
        String vehicleType,
        String vehicleBrand,
        String vehicleModel,
        String problemCategory,
        String description,
        String location,
        String status,
        LocalDateTime requestedAt,
        String serviceDetails,
        BigDecimal latitude,
        BigDecimal longitude,
        Double distanceKm) {

    public static MechanicRequestResponse from(AssistanceRequest request) {
        var vehicle = request.getVehicle();
        return new MechanicRequestResponse(
            request.getId(), vehicle.getId(), vehicle.getVehicleNumber(),
            vehicle.getVehicleType(), vehicle.getBrand(), vehicle.getModel(),
            request.getProblemCategory(), request.getDescription(), request.getLocation(),
            request.getStatus().name(), request.getRequestedAt(), request.getCompletionNotes(),
            request.getLatitude(), request.getLongitude(), null);
    }

    public static MechanicRequestResponse from(AssistanceRequest request, Double distanceKm) {
        var response = from(request);
        return new MechanicRequestResponse(response.id(), response.vehicleId(), response.vehicleNumber(),
            response.vehicleType(), response.vehicleBrand(), response.vehicleModel(),
            response.problemCategory(), response.description(), response.location(), response.status(),
            response.requestedAt(), response.serviceDetails(), response.latitude(),
            response.longitude(), distanceKm);
    }
}
