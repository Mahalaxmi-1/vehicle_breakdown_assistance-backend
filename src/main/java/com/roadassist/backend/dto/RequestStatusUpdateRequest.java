package com.roadassist.backend.dto;

import com.roadassist.backend.model.AssistanceRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestStatusUpdateRequest(@NotNull AssistanceRequest.Status status,
        @Size(max = 1000) String notes) {
    public RequestStatusUpdateRequest {
        if (notes != null) notes = notes.trim();
    }
}
