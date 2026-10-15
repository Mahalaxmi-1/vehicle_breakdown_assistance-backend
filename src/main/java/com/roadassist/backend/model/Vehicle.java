package com.roadassist.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "vehicles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vehicles_vehicle_number", columnNames = "vehicle_number")
})
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "vehicle_number", nullable = false, length = 15)
    private String vehicleNumber;

    @Column(name = "vehicle_type", nullable = false, length = 20)
    private String vehicleType;

    @Column(nullable = false, length = 50)
    private String brand;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false)
    private boolean active = true;

    protected Vehicle() {
    }

    public Vehicle(User owner, String vehicleNumber, String vehicleType, String brand, String model) {
        this.owner = owner;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.brand = brand;
        this.model = model;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
