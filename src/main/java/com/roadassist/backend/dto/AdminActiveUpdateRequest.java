package com.roadassist.backend.dto;

import jakarta.validation.constraints.NotNull;

public record AdminActiveUpdateRequest(@NotNull Boolean active) { }
