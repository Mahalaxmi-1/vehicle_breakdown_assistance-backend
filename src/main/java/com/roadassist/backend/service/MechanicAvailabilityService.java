package com.roadassist.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.MechanicAvailabilityResponse;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.UserRepository;

@Service
public class MechanicAvailabilityService {
    private final UserRepository userRepository;
    private final AssistanceRequestRepository requestRepository;

    public MechanicAvailabilityService(UserRepository userRepository,
            AssistanceRequestRepository requestRepository) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional(readOnly = true)
    public MechanicAvailabilityResponse get(String email) {
        return MechanicAvailabilityResponse.from(findMechanic(email, false));
    }

    @Transactional
    public MechanicAvailabilityResponse update(String email, User.Availability availability) {
        User mechanic = findMechanic(email, true);
        if (availability == User.Availability.AVAILABLE
                && requestRepository.countByAssignedMechanicIdAndStatusIn(mechanic.getId(),
                    java.util.List.of(AssistanceRequest.Status.ACCEPTED,
                        AssistanceRequest.Status.ON_THE_WAY,
                        AssistanceRequest.Status.IN_SERVICE)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Complete active service requests before becoming available");
        }
        mechanic.setAvailability(availability);
        return MechanicAvailabilityResponse.from(userRepository.save(mechanic));
    }

    private User findMechanic(String email, boolean lock) {
        var user = lock
            ? userRepository.findByEmailIgnoreCaseForUpdate(email)
            : userRepository.findByEmailIgnoreCase(email);
        return user.filter(account -> account.getRole() == User.Role.MECHANIC
                && account.isActive() && account.getAvailability() != null)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Active mechanic account required"));
    }
}
