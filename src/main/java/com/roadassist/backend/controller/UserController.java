
package com.roadassist.backend.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.LoginRequest;
import com.roadassist.backend.dto.RegisterRequest;
import com.roadassist.backend.dto.UserResponse;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final UserRepository userRepository;

    public UserController(
            UserService userService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            UserRepository userRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = userService.register(request);

        Map<String, Object> response = Map.of(
                "message", "Account registered successfully",
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    request.getEmail().trim(), request.getPassword()));
        } catch (DisabledException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "This account is inactive");
        } catch (AuthenticationException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String expectedRole = "ROLE_" + request.getRole().trim();
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority(expectedRole))) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "Selected role does not match this account");
        }

        if (servletRequest.getSession(false) != null) {
            servletRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new AuthenticationServiceException("Authenticated account was not found"));
        return ResponseEntity.ok(Map.of(
            "message", "Login successful",
            "id", user.getId(),
            "fullName", user.getFullName(),
            "email", user.getEmail(),
            "role", user.getRole().name()));
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = csrfToken.getToken();
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return Map.of("token", token);
    }

    @GetMapping("/me")
    public UserResponse currentUser(Authentication authentication, HttpServletRequest request) {
        User user = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (user == null || !user.isActive()) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Session is no longer valid");
        }
        return UserResponse.from(user);
    }

}
