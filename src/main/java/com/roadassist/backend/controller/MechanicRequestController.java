package com.roadassist.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.MechanicRequestResponse;
import com.roadassist.backend.dto.MechanicRequestStatsResponse;
import com.roadassist.backend.dto.RequestStatusUpdateRequest;
import com.roadassist.backend.service.AssistanceRequestService;

@RestController
@RequestMapping("/api/mechanic/requests")
public class MechanicRequestController {
    private final AssistanceRequestService requestService;

    public MechanicRequestController(AssistanceRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    public List<MechanicRequestResponse> available(
            @AuthenticationPrincipal UserDetails principal) {
        return requestService.listAvailableForMechanics(principal.getUsername());
    }

    @GetMapping("/mine")
    public List<MechanicRequestResponse> assigned(
            @AuthenticationPrincipal UserDetails principal) {
        return requestService.listAssignedToMechanic(principal.getUsername());
    }

    @GetMapping("/stats")
    public MechanicRequestStatsResponse stats(
            @AuthenticationPrincipal UserDetails principal) {
        return requestService.mechanicStats(principal.getUsername());
    }

    @PostMapping("/{requestId}/accept")
    @ResponseStatus(HttpStatus.OK)
    public MechanicRequestResponse accept(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long requestId) {
        return requestService.accept(requestId, principal.getUsername());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{requestId}/status")
    public MechanicRequestResponse updateStatus(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long requestId,
            @jakarta.validation.Valid @RequestBody RequestStatusUpdateRequest request) {
        return requestService.updateStatus(requestId, principal.getUsername(),
            request.status(), request.notes());
    }
}
