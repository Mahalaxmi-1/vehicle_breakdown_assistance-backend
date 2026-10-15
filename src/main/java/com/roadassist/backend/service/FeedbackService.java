package com.roadassist.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.FeedbackCreateRequest;
import com.roadassist.backend.dto.FeedbackEligibleRequestResponse;
import com.roadassist.backend.dto.FeedbackResponse;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.Feedback;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.FeedbackRepository;
import com.roadassist.backend.repository.UserRepository;

@Service
public class FeedbackService {
    private final UserRepository userRepository;
    private final AssistanceRequestRepository requestRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(UserRepository userRepository,
            AssistanceRequestRepository requestRepository, FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public List<FeedbackEligibleRequestResponse> ownerRequests(String email) {
        User owner = findOwner(email);
        return requestRepository.findAllByOwnerIdAndStatusOrderByRequestedAtDesc(
                owner.getId(), AssistanceRequest.Status.COMPLETED)
            .stream().filter(request -> request.getAssignedMechanic() != null)
            .map(request -> FeedbackEligibleRequestResponse.from(request,
                feedbackRepository.findByRequestId(request.getId()).orElse(null)))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listForOwner(String email) {
        User owner = findOwner(email);
        return feedbackRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId())
            .stream().map(FeedbackResponse::from).toList();
    }

    @Transactional
    public FeedbackResponse create(String email, Long requestId, FeedbackCreateRequest input) {
        User owner = findOwner(email);
        AssistanceRequest request = requestRepository.findByIdAndOwnerId(requestId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (request.getStatus() != AssistanceRequest.Status.COMPLETED
                || request.getAssignedMechanic() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Feedback is available after a completed mechanic service");
        }
        if (feedbackRepository.existsByRequestId(requestId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Feedback has already been submitted");
        }
        Feedback feedback = feedbackRepository.saveAndFlush(new Feedback(request, owner,
            request.getAssignedMechanic(), input.rating(), input.comment()));
        return FeedbackResponse.from(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listAll() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(FeedbackResponse::from).toList();
    }

    private User findOwner(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .filter(user -> user.getRole() == User.Role.OWNER && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner account required"));
    }
}
