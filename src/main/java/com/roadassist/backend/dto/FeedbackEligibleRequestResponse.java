package com.roadassist.backend.dto;

import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.Feedback;

public record FeedbackEligibleRequestResponse(Long requestId, String vehicleNumber,
        String problemCategory, String mechanicName, String status, FeedbackResponse feedback) {
    public static FeedbackEligibleRequestResponse from(AssistanceRequest request, Feedback feedback) {
        return new FeedbackEligibleRequestResponse(request.getId(),
            request.getVehicle().getVehicleNumber(), request.getProblemCategory(),
            request.getAssignedMechanic().getFullName(), request.getStatus().name(),
            feedback == null ? null : FeedbackResponse.from(feedback));
    }
}
