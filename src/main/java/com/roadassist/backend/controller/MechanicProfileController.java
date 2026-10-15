package com.roadassist.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.MechanicProfileResponse;
import com.roadassist.backend.dto.MechanicProfileUpdateRequest;
import com.roadassist.backend.service.MechanicProfileService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mechanic/profile")
public class MechanicProfileController {
    private final MechanicProfileService profileService;
    public MechanicProfileController(MechanicProfileService profileService) { this.profileService = profileService; }

    @GetMapping
    public MechanicProfileResponse get(@AuthenticationPrincipal UserDetails principal) {
        return profileService.get(principal.getUsername());
    }

    @PutMapping
    public MechanicProfileResponse update(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MechanicProfileUpdateRequest request) {
        return profileService.update(principal.getUsername(), request);
    }
}
