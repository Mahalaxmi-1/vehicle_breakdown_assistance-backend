package com.roadassist.backend.dto;

public record MechanicRequestStatsResponse(
        long availableRequests,
        long activeServices,
        long completedJobs) {
}
