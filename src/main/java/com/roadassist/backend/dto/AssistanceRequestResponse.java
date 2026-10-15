package com.roadassist.backend.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import com.roadassist.backend.model.AssistanceRequest;

public record AssistanceRequestResponse(
        Long id,
        Long vehicleId,
        String vehicleNumber,
        String problemCategory,
        String description,
        String location,
        String status,
        LocalDateTime requestedAt,
        String mechanicName,
        String serviceDetails,
        BigDecimal latitude,
        BigDecimal longitude,
        String matchingStatus,
        String matchingMessage) {

    public static AssistanceRequestResponse from(AssistanceRequest request) {
        return from(request, null, null);
    }

    public static AssistanceRequestResponse from(AssistanceRequest request,
            String matchingStatus, String matchingMessage) {
        return new AssistanceRequestResponse(
            request.getId(),
            request.getVehicle().getId(),
            request.getVehicle().getVehicleNumber(),
            request.getProblemCategory(),
            request.getDescription(),
            request.getLocation(),
            request.getStatus().name(),
            request.getRequestedAt(), request.getAssignedMechanic() == null
                ? null : request.getAssignedMechanic().getFullName(), request.getCompletionNotes(),
            request.getLatitude(), request.getLongitude(), matchingStatus, matchingMessage);
    }
}
