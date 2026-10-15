package com.roadassist.backend.dto;

import java.time.LocalDateTime;

import com.roadassist.backend.model.AssistanceRequest;

public record AdminRequestResponse(Long id, String ownerName, String vehicleNumber,
        String problemCategory, String location, String mechanicName, String status,
        LocalDateTime requestedAt) {
    public static AdminRequestResponse from(AssistanceRequest request) {
        return new AdminRequestResponse(request.getId(), request.getOwner().getFullName(),
            request.getVehicle().getVehicleNumber(), request.getProblemCategory(),
            request.getLocation(), request.getAssignedMechanic() == null ? null
                : request.getAssignedMechanic().getFullName(), request.getStatus().name(),
            request.getRequestedAt());
    }
}
