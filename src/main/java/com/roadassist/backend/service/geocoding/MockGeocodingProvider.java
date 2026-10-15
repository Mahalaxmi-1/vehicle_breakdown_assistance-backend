package com.roadassist.backend.service.geocoding;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Explicit local-only provider for one synthetic address; never resolves real addresses. */
@Component
@Profile("mock-geocoder")
public class MockGeocodingProvider implements GeocodingProvider {
    public static final String SYNTHETIC_ADDRESS = "ROADASSIST SYNTHETIC LOCATION";

    @Override
    public Optional<GeocodedLocation> geocode(String address) {
        if (SYNTHETIC_ADDRESS.equals(address)) {
            return Optional.of(new GeocodedLocation(
                new BigDecimal("17.440000"), new BigDecimal("78.390000")));
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Mock geocoder only accepts its documented synthetic location. Configure an approved private geocoder for real addresses.");
    }

    @Override
    public boolean isRealAddressLookupConfigured() {
        return false;
    }
}
