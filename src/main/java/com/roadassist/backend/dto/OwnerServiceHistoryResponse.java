package com.roadassist.backend.dto;

import java.time.LocalDateTime;

import com.roadassist.backend.model.RequestStatusHistory;

public record OwnerServiceHistoryResponse(Long requestId, String vehicleNumber,
        String problemCategory, String mechanicName, String serviceDetails,
        LocalDateTime completedAt) {
    public static OwnerServiceHistoryResponse from(RequestStatusHistory entry) {
        var request = entry.getRequest();
        return new OwnerServiceHistoryResponse(request.getId(),
            request.getVehicle().getVehicleNumber(), request.getProblemCategory(),
            request.getAssignedMechanic() == null ? null : request.getAssignedMechanic().getFullName(),
            request.getCompletionNotes(), entry.getChangedAt());
    }
}
