package com.roadassist.backend.model;

import java.time.LocalDateTime;

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
@Table(name = "request_status_history")
public class RequestStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private AssistanceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private AssistanceRequest.Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private AssistanceRequest.Status toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    protected RequestStatusHistory() {
    }

    public RequestStatusHistory(AssistanceRequest request, AssistanceRequest.Status fromStatus,
            AssistanceRequest.Status toStatus, User changedBy) {
        this.request = request;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
    }

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public AssistanceRequest getRequest() { return request; }
    public AssistanceRequest.Status getFromStatus() { return fromStatus; }
    public AssistanceRequest.Status getToStatus() { return toStatus; }
    public User getChangedBy() { return changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
