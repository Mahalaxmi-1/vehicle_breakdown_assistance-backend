package com.roadassist.backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;

/** Shared eligibility rules for the mechanic queue, owner match feedback, and acceptance. */
@Service
public class MechanicMatchingService {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final UserRepository userRepository;
    private final double radiusKm;

    public MechanicMatchingService(UserRepository userRepository,
            @Value("${roadassist.matching.radius-km:25}") double radiusKm) {
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new IllegalArgumentException("Matching radius must be a positive finite value");
        }
        this.userRepository = userRepository;
        this.radiusKm = radiusKm;
    }

    public record Match(User mechanic, double distanceKm) { }

    public List<Match> eligibleMechanics(AssistanceRequest request) {
        if (!isSearchable(request)) return List.of();
        return userRepository.findAllByRoleAndActiveTrueAndAvailabilityAndMechanicLatitudeIsNotNullAndMechanicLongitudeIsNotNull(
                User.Role.MECHANIC, User.Availability.AVAILABLE)
            .stream()
            .filter(mechanic -> isEligible(mechanic, request))
            .map(mechanic -> new Match(mechanic, distanceKm(
                request.getLatitude(), request.getLongitude(),
                mechanic.getMechanicLatitude(), mechanic.getMechanicLongitude())))
            .toList();
    }

    public boolean isEligible(User mechanic, AssistanceRequest request) {
        if (mechanic == null || request == null || !isSearchable(request)) return false;
        if (mechanic.getRole() != User.Role.MECHANIC || !mechanic.isActive()
                || mechanic.getAvailability() != User.Availability.AVAILABLE
                || !validPair(mechanic.getMechanicLatitude(), mechanic.getMechanicLongitude(),
                    BigDecimal.valueOf(-90), BigDecimal.valueOf(90),
                    BigDecimal.valueOf(-180), BigDecimal.valueOf(180))) {
            return false;
        }

        // Specialization mapping: normalized exact category matches are eligible;
        // "General" is the explicit catch-all specialization.
        String specialization = normalize(mechanic.getSpecialization());
        String category = normalize(request.getProblemCategory());
        if (specialization.isEmpty() || category.isEmpty()
                || (!specialization.equals(category) && !specialization.equals("general"))) {
            return false;
        }

        return distanceKm(request.getLatitude(), request.getLongitude(),
            mechanic.getMechanicLatitude(), mechanic.getMechanicLongitude()) <= radiusKm;
    }

    public boolean isSearchable(AssistanceRequest request) {
        return request != null
            && request.getStatus() == AssistanceRequest.Status.REQUESTED
            && request.getAssignedMechanic() == null
            && validPair(request.getLatitude(), request.getLongitude(),
                BigDecimal.valueOf(-90), BigDecimal.valueOf(90),
                BigDecimal.valueOf(-180), BigDecimal.valueOf(180));
    }

    public boolean requestHasCoordinatePair(AssistanceRequest request) {
        return request != null && request.getLatitude() != null && request.getLongitude() != null;
    }

    public String ownerMatchStatus(AssistanceRequest request) {
        if (request.getAssignedMechanic() != null) return "MECHANIC_ASSIGNED";
        if (request.getStatus() != AssistanceRequest.Status.REQUESTED) return "NOT_SEARCHING";
        if (!requestHasCoordinatePair(request)) return "LOCATION_REQUIRED";
        return eligibleMechanics(request).isEmpty() ? "NO_ELIGIBLE_MECHANIC" : "MECHANIC_FOUND";
    }

    public String ownerMatchMessage(AssistanceRequest request, String status) {
        return switch (status) {
            case "MECHANIC_ASSIGNED" -> "A mechanic has accepted this request.";
            case "NOT_SEARCHING" -> "This request is no longer searching for a mechanic.";
            case "LOCATION_REQUIRED" -> "This request needs a resolved service address before nearby matching can run. Your request remains active.";
            case "MECHANIC_FOUND" -> "Eligible nearby mechanics were found. Your request remains open until one accepts it.";
            default -> "No eligible mechanic was found yet. Your request remains active and will be checked again as mechanic availability and profiles change.";
        };
    }

    public double distanceKm(BigDecimal latitude1, BigDecimal longitude1,
            BigDecimal latitude2, BigDecimal longitude2) {
        double lat1 = Math.toRadians(latitude1.doubleValue());
        double lat2 = Math.toRadians(latitude2.doubleValue());
        double latitudeDelta = lat2 - lat1;
        double longitudeDelta = Math.toRadians(longitude2.doubleValue() - longitude1.doubleValue());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(lat1) * Math.cos(lat2)
            * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double boundedA = Math.max(0, Math.min(1, a));
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(boundedA));
    }

    private static boolean validPair(BigDecimal latitude, BigDecimal longitude,
            BigDecimal minimumLatitude, BigDecimal maximumLatitude,
            BigDecimal minimumLongitude, BigDecimal maximumLongitude) {
        return latitude != null && longitude != null
            && latitude.compareTo(minimumLatitude) >= 0
            && latitude.compareTo(maximumLatitude) <= 0
            && longitude.compareTo(minimumLongitude) >= 0
            && longitude.compareTo(maximumLongitude) <= 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
