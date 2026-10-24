
package com.roadassist.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;

public interface AssistanceRequestRepository
        extends JpaRepository<AssistanceRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AssistanceRequest r WHERE r.id = :requestId")
    Optional<AssistanceRequest> findByIdForUpdate(
            @Param("requestId") Long requestId);

    Optional<AssistanceRequest> findByIdAndOwnerId(
            Long id, Long ownerId);

    List<AssistanceRequest> findAllByOwnerIdAndStatusOrderByRequestedAtDesc(
            Long ownerId, AssistanceRequest.Status status);

    List<AssistanceRequest> findAllByOwnerIdOrderByRequestedAtDesc(
            Long ownerId);

    List<AssistanceRequest> findAllByStatusAndAssignedMechanicIsNullOrderByRequestedAtAsc(
            AssistanceRequest.Status status);

    List<AssistanceRequest> findAllByAssignedMechanicIdOrderByRequestedAtDesc(
            Long mechanicId);

    long countByStatusAndAssignedMechanicIsNull(
            AssistanceRequest.Status status);

    long countByAssignedMechanicIdAndStatusIn(
            Long mechanicId,
            Collection<AssistanceRequest.Status> statuses);

    long countByStatus(AssistanceRequest.Status status);

    /*
     * Atomically assign a requested breakdown to one mechanic.
     * The conditional update prevents two mechanics from accepting
     * the same request.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AssistanceRequest r
            SET r.assignedMechanic = :mechanic,
                r.status = :accepted
            WHERE r.id = :requestId
              AND r.status = :requested
              AND r.assignedMechanic IS NULL
            """)
    int claimIfAvailable(
            @Param("requestId") Long requestId,
            @Param("mechanic") User mechanic,
            @Param("requested") AssistanceRequest.Status requested,
            @Param("accepted") AssistanceRequest.Status accepted);
}
