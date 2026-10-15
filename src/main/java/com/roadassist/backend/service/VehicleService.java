package com.roadassist.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.VehicleRequest;
import com.roadassist.backend.dto.VehicleResponse;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;

@Service
@Transactional
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(String email) {
        User owner = owner(email);
        return vehicleRepository.findAllByOwnerIdAndActiveTrueOrderByIdDesc(owner.getId())
            .stream().map(VehicleResponse::from).toList();
    }

    public VehicleResponse create(String email, VehicleRequest request) {
        User owner = owner(email);
        ensureRegistrationAvailable(request.getVehicleNumber(), null);
        return VehicleResponse.from(vehicleRepository.save(new Vehicle(owner,
            request.getVehicleNumber(), request.getVehicleType(), request.getBrand(), request.getModel())));
    }

    public VehicleResponse update(String email, long id, VehicleRequest request) {
        User owner = owner(email);
        Vehicle vehicle = vehicle(id, owner.getId());
        ensureRegistrationAvailable(request.getVehicleNumber(), id);
        vehicle.setVehicleNumber(request.getVehicleNumber());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setBrand(request.getBrand());
        vehicle.setModel(request.getModel());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    public void archive(String email, long id) {
        User owner = owner(email);
        Vehicle vehicle = vehicle(id, owner.getId());
        vehicle.setActive(false);
        vehicleRepository.save(vehicle);
    }

    private User owner(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .filter(user -> user.getRole() == User.Role.OWNER && user.isActive())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner account required"));
    }

    private Vehicle vehicle(long id, long ownerId) {
        return vehicleRepository.findByIdAndOwnerIdAndActiveTrue(id, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private void ensureRegistrationAvailable(String registration, Long currentId) {
        boolean exists = currentId == null
            ? vehicleRepository.existsByVehicleNumberIgnoreCase(registration)
            : vehicleRepository.existsByVehicleNumberIgnoreCaseAndIdNot(registration, currentId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This vehicle number is already registered");
        }
    }
}
