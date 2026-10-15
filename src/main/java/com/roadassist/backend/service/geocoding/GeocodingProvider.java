package com.roadassist.backend.service.geocoding;

import java.util.Optional;

/** Provider abstraction so RoadAssist can switch from the local Nominatim-compatible service. */
public interface GeocodingProvider {
    Optional<GeocodedLocation> geocode(String address);

    /** True only when this instance can resolve real-world addresses. */
    boolean isRealAddressLookupConfigured();
}
