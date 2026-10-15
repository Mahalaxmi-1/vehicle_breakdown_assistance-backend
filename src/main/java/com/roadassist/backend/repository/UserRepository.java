
package com.roadassist.backend.repository;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roadassist.backend.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(User.Role role);
    Page<User> findAllByRole(User.Role role, Pageable pageable);
    long countByActiveTrue();

    List<User> findAllByRoleAndActiveTrueAndAvailabilityAndMechanicLatitudeIsNotNullAndMechanicLongitudeIsNotNull(
            User.Role role, User.Availability availability);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);
}
