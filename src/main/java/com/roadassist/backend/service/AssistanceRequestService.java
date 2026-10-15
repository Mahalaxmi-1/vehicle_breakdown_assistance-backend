package com.roadassist.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.AssistanceRequestCreateRequest;
import com.roadassist.backend.dto.AssistanceRequestResponse;
import com.roadassist.backend.dto.MechanicRequestResponse;
import com.roadassist.backend.dto.MechanicRequestStatsResponse;
import com.roadassist.backend.dto.RequestStatusHistoryResponse;
import com.roadassist.backend.dto.OwnerServiceHistoryResponse;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.RequestStatusHistory;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;

@Service
public class AssistanceRequestService {
    private final AssistanceRequestRepository requestRepository;
    private final RequestStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final MechanicMatchingService matchingService;
    private final AddressGeocodingService geocodingService;

    public AssistanceRequestService(AssistanceRequestRepository requestRepository,
            RequestStatusHistoryRepository historyRepository,
            UserRepository userRepository, VehicleRepository vehicleRepository,
            ServiceCategoryRepository categoryRepository,
            MechanicMatchingService matchingService,
            AddressGeocodingService geocodingService) {
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.categoryRepository = categoryRepository;
        this.matchingService = matchingService;
        this.geocodingService = geocodingService;
    }

    @Transactional
    public AssistanceRequestResponse create(String authenticatedEmail,
            AssistanceRequestCreateRequest input) {
        // Resolve before database access so a slow provider does not hold a DB connection.
        var coordinates = geocodingService.resolve(input.getLocation());
        User owner = findOwner(authenticatedEmail);
        if (!categoryRepository.existsByNameIgnoreCaseAndActiveTrue(input.getProblemCategory())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid problem category");
        }
        Vehicle vehicle = vehicleRepository
            .findByIdAndOwnerIdAndActiveTrue(input.getVehicleId(), owner.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Vehicle not found"));

        AssistanceRequest request = requestRepository.saveAndFlush(new AssistanceRequest(
            owner, vehicle, input.getProblemCategory(), input.getDescription(), input.getLocation(),
            coordinates.latitude(), coordinates.longitude()));
        historyRepository.saveAndFlush(new RequestStatusHistory(
            request, null, AssistanceRequest.Status.REQUESTED, owner));

        return ownerResponse(request);
    }

    @Transactional(readOnly = true)
    public List<AssistanceRequestResponse> listForOwner(String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        return requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(owner.getId())
            .stream().map(this::ownerResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestStatusHistoryResponse> historyForOwner(Long requestId,
            String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        AssistanceRequest request = requestRepository.findByIdAndOwnerId(requestId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        return historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId())
            .stream().map(RequestStatusHistoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerServiceHistoryResponse> completedHistoryForOwner(String authenticatedEmail) {
        User owner = findOwner(authenticatedEmail);
        return historyRepository.findAllByRequestOwnerIdAndToStatusOrderByChangedAtDesc(
                owner.getId(), AssistanceRequest.Status.COMPLETED)
            .stream().map(OwnerServiceHistoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MechanicRequestResponse> listAvailableForMechanics(String authenticatedEmail) {
        User mechanic = findMechanic(authenticatedEmail);
        if (mechanic.getAvailability() != User.Availability.AVAILABLE) {
            return List.of();
        }
        return requestRepository.findAllByStatusAndAssignedMechanicIsNullOrderByRequestedAtAsc(
                AssistanceRequest.Status.REQUESTED).stream()
            .filter(request -> matchingService.isEligible(mechanic, request))
            .map(request -> MechanicRequestResponse.from(request, matchingService.distanceKm(
                request.getLatitude(), request.getLongitude(),
                mechanic.getMechanicLatitude(), mechanic.getMechanicLongitude())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MechanicRequestResponse> listAssignedToMechanic(String authenticatedEmail) {
        User mechanic = findMechanic(authenticatedEmail);
        return requestRepository.findAllByAssignedMechanicIdOrderByRequestedAtDesc(mechanic.getId())
            .stream().map(MechanicRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MechanicRequestStatsResponse mechanicStats(String authenticatedEmail) {
        User mechanic = findMechanic(authenticatedEmail);
        long eligibleAvailable = mechanic.getAvailability() == User.Availability.AVAILABLE
            ? requestRepository.findAllByStatusAndAssignedMechanicIsNullOrderByRequestedAtAsc(
                AssistanceRequest.Status.REQUESTED).stream()
                .filter(request -> matchingService.isEligible(mechanic, request)).count()
            : 0;
        return new MechanicRequestStatsResponse(
            eligibleAvailable,
            requestRepository.countByAssignedMechanicIdAndStatusIn(mechanic.getId(),
                List.of(AssistanceRequest.Status.ACCEPTED,
                    AssistanceRequest.Status.ON_THE_WAY,
                    AssistanceRequest.Status.IN_SERVICE)),
            requestRepository.countByAssignedMechanicIdAndStatusIn(mechanic.getId(),
                List.of(AssistanceRequest.Status.COMPLETED)));
    }

    @Transactional
    public MechanicRequestResponse accept(Long requestId, String authenticatedEmail) {
        User mechanic = userRepository.findByEmailIgnoreCaseForUpdate(authenticatedEmail)
            .filter(user -> user.getRole() == User.Role.MECHANIC && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Active mechanic account required"));
        if (mechanic.getAvailability() != User.Availability.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mechanic must be available to accept a request");
        }
        AssistanceRequest candidate = requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!matchingService.isEligible(mechanic, candidate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Request is not eligible for this mechanic");
        }
        int claimed = requestRepository.claimIfAvailable(requestId, mechanic,
            AssistanceRequest.Status.REQUESTED, AssistanceRequest.Status.ACCEPTED);
        if (claimed == 0) {
            if (!requestRepository.existsById(requestId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Request is no longer available");
        }

        AssistanceRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        historyRepository.saveAndFlush(new RequestStatusHistory(request,
            AssistanceRequest.Status.REQUESTED, AssistanceRequest.Status.ACCEPTED, mechanic));
        mechanic.setAvailability(User.Availability.BUSY);
        userRepository.saveAndFlush(mechanic);
        return MechanicRequestResponse.from(request);
    }

    @Transactional
    public MechanicRequestResponse updateStatus(Long requestId, String authenticatedEmail,
            AssistanceRequest.Status requestedStatus, String notes) {
        User mechanic = userRepository.findByEmailIgnoreCaseForUpdate(authenticatedEmail)
            .filter(user -> user.getRole() == User.Role.MECHANIC && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Active mechanic account required"));
        AssistanceRequest request = requestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (request.getAssignedMechanic() == null
                || !request.getAssignedMechanic().getId().equals(mechanic.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        }
        AssistanceRequest.Status next = switch (request.getStatus()) {
            case ACCEPTED -> AssistanceRequest.Status.ON_THE_WAY;
            case ON_THE_WAY -> AssistanceRequest.Status.IN_SERVICE;
            case IN_SERVICE -> AssistanceRequest.Status.COMPLETED;
            default -> null;
        };
        if (next == null || next != requestedStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid status transition");
        }
        if (next == AssistanceRequest.Status.COMPLETED
                && (notes == null || notes.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Service details are required to complete a request");
        }
        AssistanceRequest.Status previous = request.getStatus();
        request.setStatus(next);
        if (next == AssistanceRequest.Status.COMPLETED) {
            request.setCompletionNotes(notes.trim());
        }
        requestRepository.saveAndFlush(request);
        historyRepository.saveAndFlush(new RequestStatusHistory(request, previous, next, mechanic));
        return MechanicRequestResponse.from(request);
    }

    private User findOwner(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .filter(user -> user.getRole() == User.Role.OWNER && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Owner account required"));
    }

    private AssistanceRequestResponse ownerResponse(AssistanceRequest request) {
        String matchStatus = matchingService.ownerMatchStatus(request);
        return AssistanceRequestResponse.from(request, matchStatus,
            matchingService.ownerMatchMessage(request, matchStatus));
    }

    private User findMechanic(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .filter(user -> user.getRole() == User.Role.MECHANIC && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Active mechanic account required"));
    }
}
