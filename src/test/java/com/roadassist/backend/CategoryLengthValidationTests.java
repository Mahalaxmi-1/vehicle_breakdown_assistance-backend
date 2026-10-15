package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.roadassist.backend.dto.AdminCategoryCreateRequest;
import com.roadassist.backend.dto.AssistanceRequestCreateRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CategoryLengthValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void adminCategoryNameCannotExceedRequestStorageLength() {
        var accepted = new AdminCategoryCreateRequest("x".repeat(20), "Valid description");
        var rejected = new AdminCategoryCreateRequest("x".repeat(21), "Valid description");

        assertTrue(validator.validate(accepted).isEmpty());
        assertEquals(1, validator.validate(rejected).size());
    }

    @Test
    void requestCategoryCannotExceedRequestStorageLength() {
        var accepted = request("x".repeat(20));
        var rejected = request("x".repeat(21));

        assertTrue(validator.validate(accepted).isEmpty());
        assertEquals(1, validator.validate(rejected).size());
    }

    private AssistanceRequestCreateRequest request(String category) {
        var request = new AssistanceRequestCreateRequest();
        request.setVehicleId(1L);
        request.setProblemCategory(category);
        request.setDescription("A valid issue description");
        request.setLocation("Main Street");
        return request;
    }
}
