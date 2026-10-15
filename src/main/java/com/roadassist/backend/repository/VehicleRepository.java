package com.roadassist.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roadassist.backend.model.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findAllByOwnerIdAndActiveTrueOrderByIdDesc(Long ownerId);
    Optional<Vehicle> findByIdAndOwnerIdAndActiveTrue(Long id, Long ownerId);
    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);
    boolean existsByVehicleNumberIgnoreCaseAndIdNot(String vehicleNumber, Long id);
}
