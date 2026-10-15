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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.MechanicRequestResponse;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.service.AddressGeocodingService;
import com.roadassist.backend.service.AssistanceRequestService;
import com.roadassist.backend.service.MechanicMatchingService;

class AssistanceRequestStatusTransitionTests {

    private AssistanceRequestRepository requestRepository;
    private RequestStatusHistoryRepository historyRepository;
    private UserRepository userRepository;
    private VehicleRepository vehicleRepository;
    private ServiceCategoryRepository categoryRepository;
    private MechanicMatchingService matchingService;
    private AddressGeocodingService geocodingService;
    private AssistanceRequestService service;

    private User mechanic;
    private User otherMechanic;
    private User owner;
    private Vehicle vehicle;
    private AssistanceRequest request;

    @BeforeEach
    void setUp() {
        requestRepository = mock(AssistanceRequestRepository.class);
        historyRepository = mock(RequestStatusHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        categoryRepository = mock(ServiceCategoryRepository.class);
        matchingService = mock(MechanicMatchingService.class);
        geocodingService = mock(AddressGeocodingService.class);

        service = new AssistanceRequestService(requestRepository, historyRepository, userRepository,
            vehicleRepository, categoryRepository, matchingService, geocodingService);

        owner = new User();
        ReflectionTestUtils.setField(owner, "id", 1L);
        owner.setFullName("Car Owner");
        owner.setEmail("owner@example.test");
        owner.setPhone("9000000001");
        owner.setRole(User.Role.OWNER);
        owner.setActive(true);

        mechanic = new User();
        ReflectionTestUtils.setField(mechanic, "id", 2L);
        mechanic.setFullName("Main Mechanic");
        mechanic.setEmail("mechanic@example.test");
        mechanic.setPhone("9000000002");
        mechanic.setRole(User.Role.MECHANIC);
        mechanic.setActive(true);

        otherMechanic = new User();
        ReflectionTestUtils.setField(otherMechanic, "id", 3L);
        otherMechanic.setFullName("Other Mechanic");
        otherMechanic.setEmail("other@example.test");
        otherMechanic.setPhone("9000000003");
        otherMechanic.setRole(User.Role.MECHANIC);
        otherMechanic.setActive(true);

        vehicle = new Vehicle(owner, "TS07AB1234", "Car", "Maruti", "Swift");
        ReflectionTestUtils.setField(vehicle, "id", 10L);

        request = new AssistanceRequest(owner, vehicle, "Tyre", "Flat tyre", "Highway 65",
            new BigDecimal("17.440000"), new BigDecimal("78.390000"));
        ReflectionTestUtils.setField(request, "id", 100L);
        ReflectionTestUtils.setField(request, "assignedMechanic", mechanic);
        request.setStatus(AssistanceRequest.Status.ACCEPTED);

        when(userRepository.findByEmailIgnoreCaseForUpdate("mechanic@example.test"))
            .thenReturn(Optional.of(mechanic));
        when(requestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));
    }

    @Test
    void acceptedToOnTheWaySucceeds() {
        MechanicRequestResponse response = service.updateStatus(100L, "mechanic@example.test",
            AssistanceRequest.Status.ON_THE_WAY, null);

        assertNotNull(response);
        assertEquals(AssistanceRequest.Status.ON_THE_WAY.name(), response.status());
        verify(requestRepository).saveAndFlush(request);
        verify(historyRepository).saveAndFlush(any());
    }

    @Test
    void onTheWayToInServiceSucceeds() {
        request.setStatus(AssistanceRequest.Status.ON_THE_WAY);

        MechanicRequestResponse response = service.updateStatus(100L, "mechanic@example.test",
            AssistanceRequest.Status.IN_SERVICE, null);

        assertNotNull(response);
        assertEquals(AssistanceRequest.Status.IN_SERVICE.name(), response.status());
        verify(requestRepository).saveAndFlush(request);
        verify(historyRepository).saveAndFlush(any());
    }

    @Test
    void inServiceToCompletedWithNotesSucceeds() {
        request.setStatus(AssistanceRequest.Status.IN_SERVICE);

        MechanicRequestResponse response = service.updateStatus(100L, "mechanic@example.test",
            AssistanceRequest.Status.COMPLETED, "Replaced punctured front tyre with spare.");

        assertNotNull(response);
        assertEquals(AssistanceRequest.Status.COMPLETED.name(), response.status());
        assertEquals("Replaced punctured front tyre with spare.", request.getCompletionNotes());
        verify(requestRepository).saveAndFlush(request);
        verify(historyRepository).saveAndFlush(any());
    }

    @Test
    void inServiceToCompletedWithoutNotesThrowsBadRequest() {
        request.setStatus(AssistanceRequest.Status.IN_SERVICE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.updateStatus(100L, "mechanic@example.test", AssistanceRequest.Status.COMPLETED, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Service details are required to complete a request", ex.getReason());
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void skippingStagesThrowsConflict() {
        // Request is currently ACCEPTED, attempting to jump directly to COMPLETED
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.updateStatus(100L, "mechanic@example.test", AssistanceRequest.Status.COMPLETED, "Notes"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Invalid status transition", ex.getReason());
    }

    @Test
    void unassignedMechanicCannotUpdateStatus() {
        when(userRepository.findByEmailIgnoreCaseForUpdate("other@example.test"))
            .thenReturn(Optional.of(otherMechanic));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.updateStatus(100L, "other@example.test", AssistanceRequest.Status.ON_THE_WAY, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(requestRepository, never()).saveAndFlush(any());
    }
}
