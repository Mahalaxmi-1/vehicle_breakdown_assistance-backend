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

import com.roadassist.backend.dto.FeedbackCreateRequest;
import com.roadassist.backend.dto.FeedbackEligibleRequestResponse;
import com.roadassist.backend.dto.FeedbackResponse;
import com.roadassist.backend.service.FeedbackService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/owner/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public List<FeedbackEligibleRequestResponse> eligible(
            @AuthenticationPrincipal UserDetails principal) {
        return feedbackService.ownerRequests(principal.getUsername());
    }

    @GetMapping("/mine")
    public List<FeedbackResponse> mine(@AuthenticationPrincipal UserDetails principal) {
        return feedbackService.listForOwner(principal.getUsername());
    }

    @PostMapping("/{requestId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(@AuthenticationPrincipal UserDetails principal,
            @PathVariable Long requestId, @Valid @RequestBody FeedbackCreateRequest input) {
        return feedbackService.create(principal.getUsername(), requestId, input);
    }
}
