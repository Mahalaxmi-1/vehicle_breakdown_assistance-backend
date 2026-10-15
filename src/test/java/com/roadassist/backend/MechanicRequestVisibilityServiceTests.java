package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.service.AssistanceRequestService;
import com.roadassist.backend.service.MechanicMatchingService;

class MechanicRequestVisibilityServiceTests {
    private static final String EMAIL = "mechanic@example.test";

    private final AssistanceRequestRepository requests = mock(AssistanceRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MechanicMatchingService matching = new MechanicMatchingService(users, 25.0);
    private final AssistanceRequestService service = new AssistanceRequestService(requests,
        mock(RequestStatusHistoryRepository.class), users, mock(VehicleRepository.class),
        mock(ServiceCategoryRepository.class), matching,
        mock(com.roadassist.backend.service.AddressGeocodingService.class));

    @Test
    void onlyEligibleOpenRequestsAreReturnedToAuthenticatedMechanic() {
        User mechanic = mechanic(User.Availability.AVAILABLE);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(mechanic));
        AssistanceRequest eligible = request("Battery", decimal("0.1"), decimal("0"));
        AssistanceRequest wrongCategory = request("Tyre", decimal("0.1"), decimal("0"));
        AssistanceRequest outsideRadius = request("Battery", decimal("1"), decimal("0"));
        AssistanceRequest missingLocation = request("Battery", null, null);
        when(requests.findAllByStatusAndAssignedMechanicIsNullOrderByRequestedAtAsc(
                AssistanceRequest.Status.REQUESTED))
            .thenReturn(List.of(eligible, wrongCategory, outsideRadius, missingLocation));

        var visible = service.listAvailableForMechanics(EMAIL);

        assertEquals(1, visible.size());
        assertEquals("Battery", visible.get(0).problemCategory());
        assertEquals("VEH-123", visible.get(0).vehicleNumber());
    }

    @Test
    void offlineMechanicReceivesNoRequestsAndDoesNotQueryQueue() {
        when(users.findByEmailIgnoreCase(EMAIL))
            .thenReturn(Optional.of(mechanic(User.Availability.OFFLINE)));

        assertTrue(service.listAvailableForMechanics(EMAIL).isEmpty());

        verify(requests, never()).findAllByStatusAndAssignedMechanicIsNullOrderByRequestedAtAsc(
            AssistanceRequest.Status.REQUESTED);
    }

    private static User mechanic(User.Availability availability) {
        User mechanic = new User();
        mechanic.setRole(User.Role.MECHANIC);
        mechanic.setActive(true);
        mechanic.setAvailability(availability);
        mechanic.setSpecialization("Battery");
        mechanic.setMechanicLatitude(decimal("0"));
        mechanic.setMechanicLongitude(decimal("0"));
        return mechanic;
    }

    private static AssistanceRequest request(String category, BigDecimal latitude, BigDecimal longitude) {
        User owner = new User();
        owner.setRole(User.Role.OWNER);
        Vehicle vehicle = new Vehicle(owner, "VEH-123", "Car", "Test", "Model");
        return new AssistanceRequest(owner, vehicle, category,
            "Vehicle needs roadside assistance after a breakdown.", "Test address", latitude, longitude);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
