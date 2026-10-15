package com.roadassist.backend.dto;

import java.time.LocalDateTime;

import com.roadassist.backend.model.RequestStatusHistory;

public record RequestStatusHistoryResponse(
        Long requestId,
        String fromStatus,
        String toStatus,
        String changedByRole,
        String changedByName,
        LocalDateTime changedAt) {
    public static RequestStatusHistoryResponse from(RequestStatusHistory entry) {
        return new RequestStatusHistoryResponse(entry.getRequest().getId(),
            entry.getFromStatus() == null ? null : entry.getFromStatus().name(),
            entry.getToStatus().name(),
            entry.getChangedBy().getRole().name(),
            entry.getChangedBy().getFullName(),
            entry.getChangedAt());
    }
}
