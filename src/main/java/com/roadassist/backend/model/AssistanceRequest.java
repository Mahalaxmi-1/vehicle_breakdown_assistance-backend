package com.roadassist.backend.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistance_requests")
public class AssistanceRequest {

    public enum Status {
        REQUESTED,
        ACCEPTED,
        ON_THE_WAY,
        IN_SERVICE,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_mechanic_id")
    private User assignedMechanic;

    @Column(name = "problem_category", nullable = false, length = 20)
    private String problemCategory;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 300)
    private String location;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "completion_notes", length = 1000)
    private String completionNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    protected AssistanceRequest() {
    }

    public AssistanceRequest(User owner, Vehicle vehicle, String problemCategory,
            String description, String location) {
        this(owner, vehicle, problemCategory, description, location, null, null);
    }

    public AssistanceRequest(User owner, Vehicle vehicle, String problemCategory,
            String description, String location, BigDecimal latitude, BigDecimal longitude) {
        this.owner = owner;
        this.vehicle = vehicle;
        this.problemCategory = problemCategory;
        this.description = description;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = Status.REQUESTED;
    }

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public Vehicle getVehicle() { return vehicle; }
    public User getAssignedMechanic() { return assignedMechanic; }
    public String getProblemCategory() { return problemCategory; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}
