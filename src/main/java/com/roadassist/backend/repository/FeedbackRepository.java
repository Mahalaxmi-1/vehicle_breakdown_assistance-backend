package com.roadassist.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roadassist.backend.model.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<Feedback> findAllByOrderByCreatedAtDesc();
    Optional<Feedback> findByRequestId(Long requestId);
    boolean existsByRequestId(Long requestId);
}
