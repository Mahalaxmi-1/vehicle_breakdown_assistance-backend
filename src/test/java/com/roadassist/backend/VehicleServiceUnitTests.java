package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.VehicleRequest;
import com.roadassist.backend.dto.VehicleResponse;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.service.VehicleService;

class VehicleServiceUnitTests {

    private VehicleRepository vehicleRepository;
    private UserRepository userRepository;
    private VehicleService vehicleService;

    private User owner;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        vehicleRepository = mock(VehicleRepository.class);
        userRepository = mock(UserRepository.class);
        vehicleService = new VehicleService(vehicleRepository, userRepository);

        owner = new User();
        ReflectionTestUtils.setField(owner, "id", 1L);
        owner.setFullName("Vehicle Owner");
        owner.setEmail("owner@example.test");
        owner.setPhone("9876543210");
        owner.setRole(User.Role.OWNER);
        owner.setActive(true);

        vehicle = new Vehicle(owner, "TS08EA1234", "Car", "Honda", "City");
        ReflectionTestUtils.setField(vehicle, "id", 10L);
        vehicle.setActive(true);

        when(userRepository.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(owner));
    }

    private VehicleRequest createVehicleRequest(String vehicleNumber, String type, String brand, String model) {
        VehicleRequest req = new VehicleRequest();
        req.setVehicleNumber(vehicleNumber);
        req.setVehicleType(type);
        req.setBrand(brand);
        req.setModel(model);
        return req;
    }

    @Test
    void duplicateVehicleRegistrationOnCreateThrowsConflict() {
        when(vehicleRepository.existsByVehicleNumberIgnoreCase("TS08EA1234")).thenReturn(true);

        VehicleRequest request = createVehicleRequest("TS08EA1234", "Car", "Honda", "City");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            vehicleService.create("owner@example.test", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("This vehicle number is already registered", ex.getReason());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void validVehicleCreationSavesAndReturnsResponse() {
        when(vehicleRepository.existsByVehicleNumberIgnoreCase("TS08EA1234")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);

        VehicleRequest request = createVehicleRequest("TS08EA1234", "Car", "Honda", "City");
        VehicleResponse response = vehicleService.create("owner@example.test", request);

        assertNotNull(response);
        assertEquals("TS08EA1234", response.vehicleNumber());
        assertEquals("Honda", response.brand());
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void vehicleNotOwnedThrowsNotFoundOnUpdate() {
        when(vehicleRepository.findByIdAndOwnerIdAndActiveTrue(99L, owner.getId()))
            .thenReturn(Optional.empty());

        VehicleRequest request = createVehicleRequest("TS08EA9999", "Car", "Honda", "City");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            vehicleService.update("owner@example.test", 99L, request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Vehicle not found", ex.getReason());
    }

    @Test
    void archivingVehicleSetsActiveFalse() {
        when(vehicleRepository.findByIdAndOwnerIdAndActiveTrue(10L, owner.getId()))
            .thenReturn(Optional.of(vehicle));

        vehicleService.archive("owner@example.test", 10L);

        assertFalse(vehicle.isActive());
        verify(vehicleRepository).save(vehicle);
    }
}
