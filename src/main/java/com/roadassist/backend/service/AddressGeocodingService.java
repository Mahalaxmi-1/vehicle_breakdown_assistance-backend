package com.roadassist.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.service.geocoding.GeocodedLocation;
import com.roadassist.backend.service.geocoding.GeocodingProvider;

/** Normalizes and caches successful and not-found address lookups. */
@Service
public class AddressGeocodingService {
    private static final int MAX_CACHE_ENTRIES = 1000;
    private static final long SUCCESS_TTL_MS = Duration.ofDays(1).toMillis();
    private static final long NOT_FOUND_TTL_MS = Duration.ofMinutes(5).toMillis();
    private final GeocodingProvider provider;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(128, 0.75f, true) {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public AddressGeocodingService(GeocodingProvider provider) {
        this.provider = provider;
    }

    public synchronized GeocodedLocation resolve(String address) {
        String cleanedAddress = clean(address);
        if (cleanedAddress.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Enter a complete address including area and city.");
        }
        String cacheKey = cleanedAddress.toLowerCase(Locale.ROOT);

        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && entry.expiresAtMillis() > now) {
            return entry.location().orElseThrow(this::notFound);
        }
        cache.remove(cacheKey);

        Optional<GeocodedLocation> result = provider.geocode(cleanedAddress);
        if (result.isEmpty()) {
            cache.put(cacheKey, new CacheEntry(Optional.empty(), now + NOT_FOUND_TTL_MS));
            throw notFound();
        }

        GeocodedLocation location = result.get();
        if (!validRange(location.latitude(), BigDecimal.valueOf(-90), BigDecimal.valueOf(90))
                || !validRange(location.longitude(), BigDecimal.valueOf(-180), BigDecimal.valueOf(180))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Address lookup returned invalid coordinates. Try another address later.");
        }
        cache.put(cacheKey, new CacheEntry(Optional.of(location), now + SUCCESS_TTL_MS));
        return location;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "We could not resolve that address. Add a nearby area, city, and state, then try again.");
    }

    private static boolean validRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private static String clean(String address) {
        return address == null ? "" : address.trim().replaceAll("\\s+", " ");
    }

    private record CacheEntry(Optional<GeocodedLocation> location, long expiresAtMillis) { }
}
