package com.roadassist.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.MechanicAvailabilityResponse;
import com.roadassist.backend.dto.MechanicAvailabilityUpdateRequest;
import com.roadassist.backend.service.MechanicAvailabilityService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mechanic/availability")
public class MechanicAvailabilityController {
    private final MechanicAvailabilityService availabilityService;

    public MechanicAvailabilityController(MechanicAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public MechanicAvailabilityResponse get(
            @AuthenticationPrincipal UserDetails principal) {
        return availabilityService.get(principal.getUsername());
    }

    @PutMapping
    public MechanicAvailabilityResponse update(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MechanicAvailabilityUpdateRequest request) {
        return availabilityService.update(principal.getUsername(), request.availability());
    }
}
