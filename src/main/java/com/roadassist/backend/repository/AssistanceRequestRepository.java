package com.roadassist.backend.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import com.roadassist.backend.model.AssistanceRequest;

public interface AssistanceRequestRepository extends JpaRepository<AssistanceRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssistanceRequest r where r.id = :requestId")
    Optional<AssistanceRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    Optional<AssistanceRequest> findByIdAndOwnerId(Long id, Long ownerId);

    List<AssistanceRequest> findAllByOwnerIdAndStatusOrderByRequestedAtDesc(
            Long ownerId, AssistanceRequest.Status status);
    List<AssistanceRequest> findAllByOwnerIdOrderByRequestedAtDesc(Long ownerId);

    List<AssistanceRequest> findAllByStatusAndAssignedMechanicIsNullOrderByRequestedAtAsc(
            AssistanceRequest.Status status);

    List<AssistanceRequest> findAllByAssignedMechanicIdOrderByRequestedAtDesc(Long mechanicId);

    long countByStatusAndAssignedMechanicIsNull(AssistanceRequest.Status status);

    long countByAssignedMechanicIdAndStatusIn(Long mechanicId,
            Collection<AssistanceRequest.Status> statuses);
    long countByStatus(AssistanceRequest.Status status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AssistanceRequest r set r.assignedMechanic = :mechanic, r.status = :accepted "
            + "where r.id = :requestId and r.status = :requested and r.assignedMechanic is null")
    int claimIfAvailable(@Param("requestId") Long requestId,
            @Param("mechanic") com.roadassist.backend.model.User mechanic,
            @Param("requested") AssistanceRequest.Status requested,
            @Param("accepted") AssistanceRequest.Status accepted);
}
