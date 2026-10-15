package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import com.roadassist.backend.dto.AssistanceRequestCreateRequest;
import com.roadassist.backend.dto.MechanicProfileUpdateRequest;

class CoordinateValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestCoordinatesMustBeProvidedAsAValidPair() {
        AssistanceRequestCreateRequest request = validRequest();
        request.setLatitude(new BigDecimal("91"));
        assertFalse(validator.validate(request).isEmpty());

        request.setLatitude(new BigDecimal("17.4"));
        assertTrue(validator.validate(request).stream()
            .anyMatch(error -> error.getMessage().contains("provided together")));

        request.setLongitude(new BigDecimal("78.4"));
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void mechanicProfileCoordinatesMustBeProvidedAsAValidPair() {
        var valid = new MechanicProfileUpdateRequest("Mechanic", "9876543210", "Battery",
            "Hyderabad", 5, new BigDecimal("17.4"), new BigDecimal("78.4"));
        assertTrue(validator.validate(valid).isEmpty());

        var partial = new MechanicProfileUpdateRequest("Mechanic", "9876543210", "Battery",
            "Hyderabad", 5, new BigDecimal("17.4"), null);
        assertTrue(validator.validate(partial).stream()
            .anyMatch(error -> error.getMessage().contains("provided together")));

        var outOfRange = new MechanicProfileUpdateRequest("Mechanic", "9876543210", "Battery",
            "Hyderabad", 5, new BigDecimal("-91"), new BigDecimal("78.4"));
        assertFalse(validator.validate(outOfRange).isEmpty());
    }

    private static AssistanceRequestCreateRequest validRequest() {
        AssistanceRequestCreateRequest request = new AssistanceRequestCreateRequest();
        request.setVehicleId(1L);
        request.setProblemCategory("Battery");
        request.setDescription("The vehicle battery has stopped working.");
        request.setLocation("Test street and area");
        return request;
    }
}
