package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanDefinitionStoreException;

class TestDatabaseSafetyConfigurationTests {
    @Test
    void acceptsOnlyLocalRoadAssistTestSchema() {
        assertDoesNotThrow(() -> TestDatabaseSafetyConfiguration.validateUrl(
            "jdbc:mysql://localhost:3306/roadassist_test"));
        assertDoesNotThrow(() -> TestDatabaseSafetyConfiguration.validateUrl(
            "jdbc:mysql://127.0.0.1/roadassist_test?serverTimezone=UTC"));
    }

    @Test
    void rejectsDevelopmentAndRemoteSchemas() {
        assertThrows(BeanDefinitionStoreException.class, () ->
            TestDatabaseSafetyConfiguration.validateUrl(
                "jdbc:mysql://localhost:3306/roadassist_db"));
        assertThrows(BeanDefinitionStoreException.class, () ->
            TestDatabaseSafetyConfiguration.validateUrl(
                "jdbc:mysql://localhost:3306/roadassist_test_stage"));
        assertThrows(BeanDefinitionStoreException.class, () ->
            TestDatabaseSafetyConfiguration.validateUrl(
                "jdbc:mysql://db.example.com:3306/roadassist_test"));
        assertThrows(BeanDefinitionStoreException.class, () ->
            TestDatabaseSafetyConfiguration.validateUrl(null));
    }
}
