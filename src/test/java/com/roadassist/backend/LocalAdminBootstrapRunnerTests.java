package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.LocalAdminBootstrapRunner;

class LocalAdminBootstrapRunnerTests {
    private static Validator validator;
    private static AutoCloseable validatorFactory;

    @BeforeAll
    static void createValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        validatorFactory = factory;
    }

    @AfterAll
    static void closeValidator() throws Exception {
        validatorFactory.close();
    }

    @Test
    void createsNewAdminWithExistingPasswordEncoder() {
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(users.existsByEmailIgnoreCase("admin@roadassist.local")).thenReturn(false);

        var runner = runner(users, encoder, " admin@roadassist.local ", "StrongPassword123!");
        runner.run(new DefaultApplicationArguments(new String[0]));

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals(User.Role.ADMIN, saved.getValue().getRole());
        assertEquals("admin@roadassist.local", saved.getValue().getEmail());
        assertTrue(encoder.matches("StrongPassword123!", saved.getValue().getPassword()));
        assertTrue(saved.getValue().getPassword().startsWith("$2"));
        assertTrue(saved.getValue().isActive());
    }

    @Test
    void existingAdminMakesBootstrapAnIdempotentNoOp() {
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.existsByRole(User.Role.ADMIN)).thenReturn(true);

        runner(users, encoder, "admin@roadassist.local", "StrongPassword123!")
            .run(new DefaultApplicationArguments(new String[0]));

        verify(users, never()).saveAndFlush(any(User.class));
        verify(encoder, never()).encode(any(CharSequence.class));
    }

    @Test
    void refusesToOverwriteAnExistingAccount() {
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(users.existsByEmailIgnoreCase("admin@roadassist.local")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> runner(users, encoder,
            "admin@roadassist.local", "StrongPassword123!").run(new DefaultApplicationArguments(new String[0])));
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsInvalidBootstrapCredentialsWithoutSaving() {
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.existsByRole(User.Role.ADMIN)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> runner(users, encoder,
            "", "short").run(new DefaultApplicationArguments(new String[0])));
        verify(users, never()).saveAndFlush(any(User.class));
    }

    private LocalAdminBootstrapRunner runner(UserRepository users, PasswordEncoder encoder,
            String email, String password) {
        return new LocalAdminBootstrapRunner(users, encoder, validator,
            "RoadAssist Admin", email, "+919876543210", password);
    }
}
