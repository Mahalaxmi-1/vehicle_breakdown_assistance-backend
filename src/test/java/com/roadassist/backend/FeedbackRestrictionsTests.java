package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.FeedbackCreateRequest;
import com.roadassist.backend.dto.FeedbackResponse;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.Feedback;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.FeedbackRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.FeedbackService;

class FeedbackRestrictionsTests {

    private UserRepository userRepository;
    private AssistanceRequestRepository requestRepository;
    private FeedbackRepository feedbackRepository;
    private FeedbackService feedbackService;

    private User owner;
    private User mechanic;
    private Vehicle vehicle;
    private AssistanceRequest completedRequest;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        requestRepository = mock(AssistanceRequestRepository.class);
        feedbackRepository = mock(FeedbackRepository.class);
        feedbackService = new FeedbackService(userRepository, requestRepository, feedbackRepository);

        owner = new User();
        ReflectionTestUtils.setField(owner, "id", 10L);
        owner.setFullName("Owner Name");
        owner.setEmail("owner@example.test");
        owner.setPhone("9876543210");
        owner.setRole(User.Role.OWNER);
        owner.setActive(true);

        mechanic = new User();
        ReflectionTestUtils.setField(mechanic, "id", 20L);
        mechanic.setFullName("Mechanic Name");
        mechanic.setEmail("mechanic@example.test");
        mechanic.setPhone("9876543211");
        mechanic.setRole(User.Role.MECHANIC);
        mechanic.setActive(true);

        vehicle = new Vehicle(owner, "TS09AB1234", "Car", "Hyundai", "i20");
        ReflectionTestUtils.setField(vehicle, "id", 30L);

        completedRequest = new AssistanceRequest(owner, vehicle, "Battery", "Dead battery", "Location",
            new BigDecimal("17.440000"), new BigDecimal("78.390000"));
        ReflectionTestUtils.setField(completedRequest, "id", 100L);
        ReflectionTestUtils.setField(completedRequest, "assignedMechanic", mechanic);
        completedRequest.setStatus(AssistanceRequest.Status.COMPLETED);

        when(userRepository.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(owner));
    }

    @Test
    void nonOwnerCannotSubmitFeedback() {
        when(userRepository.findByEmailIgnoreCase("mechanic@example.test")).thenReturn(Optional.of(mechanic));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.create("mechanic@example.test", 100L, new FeedbackCreateRequest(5, "Great")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void requestNotFoundOrBelongingToAnotherOwnerThrowsNotFound() {
        when(requestRepository.findByIdAndOwnerId(999L, owner.getId())).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.create("owner@example.test", 999L, new FeedbackCreateRequest(5, "Great")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void uncompletedRequestThrowsConflict() {
        AssistanceRequest inProgress = new AssistanceRequest(owner, vehicle, "Tyre", "Puncture", "Location",
            new BigDecimal("17.440000"), new BigDecimal("78.390000"));
        ReflectionTestUtils.setField(inProgress, "id", 101L);
        ReflectionTestUtils.setField(inProgress, "assignedMechanic", mechanic);
        inProgress.setStatus(AssistanceRequest.Status.IN_SERVICE);

        when(requestRepository.findByIdAndOwnerId(101L, owner.getId())).thenReturn(Optional.of(inProgress));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.create("owner@example.test", 101L, new FeedbackCreateRequest(5, "Good")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Feedback is available after a completed mechanic service", ex.getReason());
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateFeedbackOnSameRequestThrowsConflict() {
        when(requestRepository.findByIdAndOwnerId(100L, owner.getId())).thenReturn(Optional.of(completedRequest));
        when(feedbackRepository.existsByRequestId(100L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            feedbackService.create("owner@example.test", 100L, new FeedbackCreateRequest(4, "Duplicate")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Feedback has already been submitted", ex.getReason());
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void validCompletedRequestSavesAndReturnsFeedback() {
        when(requestRepository.findByIdAndOwnerId(100L, owner.getId())).thenReturn(Optional.of(completedRequest));
        when(feedbackRepository.existsByRequestId(100L)).thenReturn(false);

        Feedback saved = new Feedback(completedRequest, owner, mechanic, 5, "Fast and friendly service");
        ReflectionTestUtils.setField(saved, "id", 500L);
        ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
        when(feedbackRepository.saveAndFlush(any(Feedback.class))).thenReturn(saved);

        FeedbackResponse response = feedbackService.create("owner@example.test", 100L,
            new FeedbackCreateRequest(5, "Fast and friendly service"));

        assertNotNull(response);
        assertEquals(5, response.rating());
        assertEquals("Fast and friendly service", response.comment());
        verify(feedbackRepository).saveAndFlush(any(Feedback.class));
    }
}
