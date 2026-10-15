package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.AssistanceRequestCreateRequest;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.service.AddressGeocodingService;
import com.roadassist.backend.service.AssistanceRequestService;
import com.roadassist.backend.service.MechanicMatchingService;

class AssistanceRequestGeocodingFailureTests {
    @Test
    void unresolvedAddressFailsBeforeAnyRequestOrHistoryPersistence() {
        var requests = mock(AssistanceRequestRepository.class);
        var history = mock(RequestStatusHistoryRepository.class);
        var users = mock(UserRepository.class);
        var vehicles = mock(VehicleRepository.class);
        var categories = mock(ServiceCategoryRepository.class);
        var geocoding = mock(AddressGeocodingService.class);
        var matching = new MechanicMatchingService(users, 25.0);
        var service = new AssistanceRequestService(requests, history, users, vehicles,
            categories, matching, geocoding);
        var input = new AssistanceRequestCreateRequest();
        input.setVehicleId(1L);
        input.setProblemCategory("Battery");
        input.setDescription("The vehicle battery has stopped working.");
        input.setLocation("Unresolved synthetic address");
        when(geocoding.resolve(input.getLocation())).thenThrow(new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "Address could not be resolved."));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create("owner@example.test", input));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
        verifyNoInteractions(requests, history, users, vehicles, categories);
    }
}
