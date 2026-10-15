package com.roadassist.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.roadassist.backend.model.AssistanceRequest;

import com.roadassist.backend.model.RequestStatusHistory;

public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistory, Long> {
    List<RequestStatusHistory> findAllByRequestIdOrderByChangedAtAsc(Long requestId);
    long countByRequestOwnerId(Long ownerId);
    List<RequestStatusHistory> findAllByRequestOwnerIdAndToStatusOrderByChangedAtDesc(
        Long ownerId, AssistanceRequest.Status toStatus);
}
