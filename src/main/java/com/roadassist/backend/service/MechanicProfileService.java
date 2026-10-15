package com.roadassist.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.MechanicProfileResponse;
import com.roadassist.backend.dto.MechanicProfileUpdateRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.geocoding.GeocodedLocation;

@Service
public class MechanicProfileService {
    private final UserRepository userRepository;
    private final AddressGeocodingService geocodingService;

    public MechanicProfileService(UserRepository userRepository,
            AddressGeocodingService geocodingService) {
        this.userRepository = userRepository;
        this.geocodingService = geocodingService;
    }

    @Transactional(readOnly = true)
    public MechanicProfileResponse get(String email) {
        return MechanicProfileResponse.from(findMechanic(email, false));
    }

    @Transactional
    public MechanicProfileResponse update(String email, MechanicProfileUpdateRequest input) {
        User mechanic = findMechanic(email, true);
        GeocodedLocation location = sameAddress(input.serviceLocation(), mechanic.getServiceLocation())
                && mechanic.getMechanicLatitude() != null && mechanic.getMechanicLongitude() != null
            ? new GeocodedLocation(mechanic.getMechanicLatitude(), mechanic.getMechanicLongitude())
            : geocodingService.resolve(input.serviceLocation());
        mechanic.setFullName(input.fullName());
        mechanic.setPhone(input.phone());
        mechanic.setSpecialization(input.specialization());
        mechanic.setServiceLocation(input.serviceLocation());
        mechanic.setExperienceYears(input.experienceYears());
        mechanic.setMechanicLatitude(location.latitude());
        mechanic.setMechanicLongitude(location.longitude());
        return MechanicProfileResponse.from(userRepository.save(mechanic));
    }

    private static boolean sameAddress(String first, String second) {
        if (first == null || second == null) return false;
        return first.trim().replaceAll("\\s+", " ").equalsIgnoreCase(
            second.trim().replaceAll("\\s+", " "));
    }

    private User findMechanic(String email, boolean lock) {
        var result = lock ? userRepository.findByEmailIgnoreCaseForUpdate(email)
            : userRepository.findByEmailIgnoreCase(email);
        return result.filter(user -> user.getRole() == User.Role.MECHANIC && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Active mechanic account required"));
    }
}
