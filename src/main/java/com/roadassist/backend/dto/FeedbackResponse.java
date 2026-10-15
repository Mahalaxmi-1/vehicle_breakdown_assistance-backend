package com.roadassist.backend.dto;

import java.time.LocalDateTime;

import com.roadassist.backend.model.Feedback;

public record FeedbackResponse(Long id, Long requestId, String vehicleNumber,
        String problemCategory, String mechanicName, int rating, String comment,
        LocalDateTime createdAt) {
    public static FeedbackResponse from(Feedback feedback) {
        var request = feedback.getRequest();
        return new FeedbackResponse(feedback.getId(), request.getId(),
            request.getVehicle().getVehicleNumber(), request.getProblemCategory(),
            feedback.getMechanic().getFullName(), feedback.getRating(),
            feedback.getComment(), feedback.getCreatedAt());
    }
}
