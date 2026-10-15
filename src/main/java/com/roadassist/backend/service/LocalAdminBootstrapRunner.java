package com.roadassist.backend.service;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;

/**
 * One-time local-only account provisioning. This is deliberately not an HTTP endpoint.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "roadassist.admin-bootstrap", name = "enabled", havingValue = "true")
public class LocalAdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminBootstrapRunner.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final BootstrapDetails details;

    public LocalAdminBootstrapRunner(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            Validator validator,
            @org.springframework.beans.factory.annotation.Value("${roadassist.admin-bootstrap.full-name:}") String fullName,
            @org.springframework.beans.factory.annotation.Value("${roadassist.admin-bootstrap.email:}") String email,
            @org.springframework.beans.factory.annotation.Value("${roadassist.admin-bootstrap.phone:}") String phone,
            @org.springframework.beans.factory.annotation.Value("${roadassist.admin-bootstrap.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.details = new BootstrapDetails(fullName, email, phone, password);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(User.Role.ADMIN)) {
            log.info("Administrator already exists; local bootstrap made no changes.");
            return;
        }

        BootstrapDetails normalized = new BootstrapDetails(
            trim(details.fullName()),
            trim(details.email()),
            trim(details.phone()),
            details.password());
        Set<jakarta.validation.ConstraintViolation<BootstrapDetails>> violations = validator.validate(normalized);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Local administrator bootstrap settings are invalid.");
        }

        String email = normalized.email().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Bootstrap email is already in use; no account was changed.");
        }

        User admin = new User();
        admin.setFullName(normalized.fullName());
        admin.setEmail(email);
        admin.setPhone(normalized.phone());
        admin.setPassword(passwordEncoder.encode(normalized.password()));
        admin.setRole(User.Role.ADMIN);
        users.saveAndFlush(admin);
        log.info("Initial local administrator provisioned successfully.");
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    record BootstrapDetails(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$") String phone,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }
}
