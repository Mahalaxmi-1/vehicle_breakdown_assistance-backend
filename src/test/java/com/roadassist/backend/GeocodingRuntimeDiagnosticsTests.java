package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.roadassist.backend.service.geocoding.GeocodingRuntimeDiagnostics;
import com.roadassist.backend.service.geocoding.MockGeocodingProvider;
import com.roadassist.backend.service.geocoding.NominatimGeocodingProvider;

class GeocodingRuntimeDiagnosticsTests {
    @Test
    void reportsMissingRealProviderWithoutExposingConfigurationValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        var provider = new NominatimGeocodingProvider("", "not-logged", 1000);

        var status = new GeocodingRuntimeDiagnostics(provider, environment).status();

        assertEquals("NominatimGeocodingProvider", status.providerImplementation());
        assertFalse(status.realAddressLookupConfigured());
        assertEquals(java.util.List.of("test"), status.activeProfiles());
        org.junit.jupiter.api.Assertions.assertFalse(status.toString().contains("not-logged"));
    }

    @Test
    void reportsMockProfileAsNotConfiguredForRealAddressLookup() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mock-geocoder");

        var status = new GeocodingRuntimeDiagnostics(new MockGeocodingProvider(), environment).status();

        assertEquals("MockGeocodingProvider", status.providerImplementation());
        assertFalse(status.realAddressLookupConfigured());
        assertEquals(java.util.List.of("mock-geocoder"), status.activeProfiles());
    }
}
