
package com.roadassist.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;

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
        String matchingMessage,
        String mechanicPhone) {

    public static AssistanceRequestResponse from(
            AssistanceRequest request) {
        return from(request, null, null);
    }

    public static AssistanceRequestResponse from(
            AssistanceRequest request,
            String matchingStatus,
            String matchingMessage) {

        User mechanic = request.getAssignedMechanic();

        return new AssistanceRequestResponse(
                request.getId(),
                request.getVehicle().getId(),
                request.getVehicle().getVehicleNumber(),
                request.getProblemCategory(),
                request.getDescription(),
                request.getLocation(),
                request.getStatus().name(),
                request.getRequestedAt(),
                mechanic == null ? null : mechanic.getFullName(),
                request.getCompletionNotes(),
                request.getLatitude(),
                request.getLongitude(),
                matchingStatus,
                matchingMessage,
                mechanic == null ? null : mechanic.getPhone());
    }
}
