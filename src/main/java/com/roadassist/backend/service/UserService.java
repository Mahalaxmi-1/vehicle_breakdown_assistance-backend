
package com.roadassist.backend.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.RegisterRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }

        User.Role role;

        try {
            role = User.Role.valueOf(request.getRole().trim());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid account role"
            );
        }

        if (role == User.Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Public registration is not available for administrator accounts"
            );
        }

        if (role == User.Role.MECHANIC) {
            if (request.getSpecialization() == null
                    || request.getSpecialization().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Specialization is required for mechanics"
                );
            }

            if (request.getServiceLocation() == null
                    || request.getServiceLocation().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Service location is required for mechanics"
                );
            }
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        if (role == User.Role.MECHANIC) {
            user.setSpecialization(request.getSpecialization().trim());
            user.setServiceLocation(request.getServiceLocation().trim());
            user.setAvailability(User.Availability.OFFLINE);
        }

        return userRepository.save(user);
    }
    
    
    public User login(com.roadassist.backend.dto.LoginRequest request) {
        String email = request.getEmail().trim();

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
            ));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
            );
        }

        if (!user.isActive()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "This account is inactive"
            );
        }

        if (!user.getRole().name().equals(request.getRole())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Selected role does not match this account"
            );
        }

        return user;
    }

}
