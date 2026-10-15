package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.MechanicMatchingService;

class MechanicMatchingServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final MechanicMatchingService matching = new MechanicMatchingService(users, 25.0);

    @Test
    void haversineDistanceUsesEarthSurfaceDistance() {
        double distance = matching.distanceKm(decimal("0"), decimal("0"), decimal("0"), decimal("1"));
        assertEquals(111.195, distance, 0.02);
    }

    @Test
    void onlyActiveAvailableNearbyMechanicsWithRelevantSpecializationMatch() {
        AssistanceRequest request = request("Battery", decimal("0"), decimal("0"));
        User closeBattery = mechanic("Battery", true, User.Availability.AVAILABLE,
            decimal("0"), decimal("0.2"));
        User closeTyre = mechanic("Tyre", true, User.Availability.AVAILABLE,
            decimal("0"), decimal("0.2"));
        User offline = mechanic("Battery", true, User.Availability.OFFLINE,
            decimal("0"), decimal("0.1"));
        User inactive = mechanic("Battery", false, User.Availability.AVAILABLE,
            decimal("0"), decimal("0.1"));
        User farAway = mechanic("Battery", true, User.Availability.AVAILABLE,
            decimal("0"), decimal("1"));
        when(users.findAllByRoleAndActiveTrueAndAvailabilityAndMechanicLatitudeIsNotNullAndMechanicLongitudeIsNotNull(
                User.Role.MECHANIC, User.Availability.AVAILABLE))
            .thenReturn(List.of(closeBattery, closeTyre, offline, inactive, farAway));

        var result = matching.eligibleMechanics(request);

        assertEquals(1, result.size());
        assertEquals(closeBattery, result.get(0).mechanic());
        assertTrue(result.get(0).distanceKm() < 25.0);
    }

    @Test
    void generalSpecializationMatchesAsDocumentedCatchAll() {
        User mechanic = mechanic(" General ", true, User.Availability.AVAILABLE,
            decimal("17.44"), decimal("78.39"));
        assertTrue(matching.isEligible(mechanic,
            request("Electrical", decimal("17.44"), decimal("78.39"))));
    }

    @Test
    void missingPartialInvalidOrUnsearchableCoordinatesNeverMatch() {
        User mechanic = mechanic("Battery", true, User.Availability.AVAILABLE,
            decimal("17.44"), decimal("78.39"));
        assertFalse(matching.isEligible(mechanic, request("Battery", null, null)));
        assertFalse(matching.isEligible(mechanic, request("Battery", decimal("17.44"), null)));
        assertFalse(matching.isEligible(mechanic,
            request("Battery", decimal("91"), decimal("78.39"))));

        AssistanceRequest completed = request("Battery", decimal("17.44"), decimal("78.39"));
        completed.setStatus(AssistanceRequest.Status.COMPLETED);
        assertFalse(matching.isEligible(mechanic, completed));

        User missingMechanicLatitude = mechanic("Battery", true, User.Availability.AVAILABLE,
            null, decimal("78.39"));
        assertFalse(matching.isEligible(missingMechanicLatitude,
            request("Battery", decimal("17.44"), decimal("78.39"))));
    }

    @Test
    void eligibleWithin25KmAndRejectedBeyondIt() {
        double earthRadiusKm = 6371.0088;
        double justInsideLongitude = Math.toDegrees(24.99 / earthRadiusKm);
        double justOutsideLongitude = Math.toDegrees(25.01 / earthRadiusKm);
        AssistanceRequest request = request("Battery", decimal("0"), decimal("0"));
        User inside = mechanic("Battery", true, User.Availability.AVAILABLE,
            decimal("0"), BigDecimal.valueOf(justInsideLongitude));
        User outside = mechanic("Battery", true, User.Availability.AVAILABLE,
            decimal("0"), BigDecimal.valueOf(justOutsideLongitude));

        assertTrue(matching.distanceKm(request.getLatitude(), request.getLongitude(),
            inside.getMechanicLatitude(), inside.getMechanicLongitude()) < 25.0);
        assertTrue(matching.isEligible(inside, request));
        assertTrue(matching.distanceKm(request.getLatitude(), request.getLongitude(),
            outside.getMechanicLatitude(), outside.getMechanicLongitude()) > 25.0);
        assertFalse(matching.isEligible(outside, request));
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }

    private static User mechanic(String specialization, boolean active,
            User.Availability availability, BigDecimal latitude, BigDecimal longitude) {
        User user = new User();
        user.setRole(User.Role.MECHANIC);
        user.setActive(active);
        user.setAvailability(availability);
        user.setSpecialization(specialization);
        user.setMechanicLatitude(latitude);
        user.setMechanicLongitude(longitude);
        return user;
    }

    private static AssistanceRequest request(String category, BigDecimal latitude, BigDecimal longitude) {
        AssistanceRequest request = new AssistanceRequest(null, null, category,
            "Vehicle has a breakdown and needs assistance.", "Test location", latitude, longitude);
        return request;
    }
}
