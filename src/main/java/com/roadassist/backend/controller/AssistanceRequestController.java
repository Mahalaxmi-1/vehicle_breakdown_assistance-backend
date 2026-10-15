package com.roadassist.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.AssistanceRequestCreateRequest;
import com.roadassist.backend.dto.AssistanceRequestResponse;
import com.roadassist.backend.service.AssistanceRequestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/owner/requests")
public class AssistanceRequestController {
    private final AssistanceRequestService requestService;

    public AssistanceRequestController(AssistanceRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssistanceRequestResponse create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AssistanceRequestCreateRequest input) {
        return requestService.create(principal.getUsername(), input);
    }

    @GetMapping
    public List<AssistanceRequestResponse> list(
            @AuthenticationPrincipal UserDetails principal) {
        return requestService.listForOwner(principal.getUsername());
    }

    @GetMapping("/{requestId}/history")
    public List<com.roadassist.backend.dto.RequestStatusHistoryResponse> history(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long requestId) {
        return requestService.historyForOwner(requestId, principal.getUsername());
    }

    @GetMapping("/history")
    public List<com.roadassist.backend.dto.OwnerServiceHistoryResponse> completedHistory(
            @AuthenticationPrincipal UserDetails principal) {
        return requestService.completedHistoryForOwner(principal.getUsername());
    }
}
