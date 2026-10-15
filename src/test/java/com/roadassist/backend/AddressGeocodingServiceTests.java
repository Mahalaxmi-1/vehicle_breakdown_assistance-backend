package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.service.AddressGeocodingService;
import com.roadassist.backend.service.geocoding.GeocodedLocation;
import com.roadassist.backend.service.geocoding.GeocodingProvider;

class AddressGeocodingServiceTests {
    private final GeocodingProvider provider = mock(GeocodingProvider.class);
    private final AddressGeocodingService geocoding = new AddressGeocodingService(provider);

    @Test
    void normalizedAddressesReuseSuccessfulCachedCoordinates() {
        var expected = new GeocodedLocation(new BigDecimal("17.440000"), new BigDecimal("78.390000"));
        when(provider.geocode("Madhapur, Hyderabad")).thenReturn(Optional.of(expected));

        assertEquals(expected, geocoding.resolve(" Madhapur,   Hyderabad "));
        assertEquals(expected, geocoding.resolve("madhapur, hyderabad"));

        verify(provider, times(1)).geocode("Madhapur, Hyderabad");
    }

    @Test
    void unresolvedAddressIsRejectedAndShortNegativeCacheAvoidsRepeatedLookup() {
        when(provider.geocode("unknown location")).thenReturn(Optional.empty());

        ResponseStatusException first = assertThrows(ResponseStatusException.class,
            () -> geocoding.resolve("unknown location"));
        ResponseStatusException second = assertThrows(ResponseStatusException.class,
            () -> geocoding.resolve(" Unknown   Location "));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, first.getStatusCode());
        assertEquals(first.getReason(), second.getReason());
        verify(provider, times(1)).geocode("unknown location");
    }

    @Test
    void invalidProviderCoordinatesAreRejected() {
        when(provider.geocode("Madhapur, Hyderabad")).thenReturn(Optional.of(
            new GeocodedLocation(new BigDecimal("91"), new BigDecimal("78.39"))));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> geocoding.resolve("Madhapur, Hyderabad"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }
}
