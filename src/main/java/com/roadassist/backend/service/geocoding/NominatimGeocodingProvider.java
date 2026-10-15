package com.roadassist.backend.service.geocoding;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** End-user-triggered Nominatim-compatible lookup against a configured private service. */
@Component
@Profile("!mock-geocoder")
public class NominatimGeocodingProvider implements GeocodingProvider {
    private static final String PUBLIC_NOMINATIM_HOST = "nominatim.openstreetmap.org";
    private static final System.Logger LOGGER = System.getLogger(NominatimGeocodingProvider.class.getName());

    private final RestClient client;
    private final String userAgent;
    private final long minIntervalNanos;
    private long nextRequestAtNanos;

    public NominatimGeocodingProvider(@Value("${roadassist.geocoding.base-url:}") String baseUrl,
            @Value("${roadassist.geocoding.user-agent:RoadAssist/1.0 (local student project)}") String userAgent,
            @Value("${roadassist.geocoding.min-interval-ms:1000}") long minIntervalMs) {
        if (minIntervalMs < 1000) {
            throw new IllegalArgumentException("Geocoder interval must be at least 1000 ms");
        }
        this.userAgent = userAgent;
        this.minIntervalNanos = TimeUnit.MILLISECONDS.toNanos(minIntervalMs);
        if (baseUrl == null || baseUrl.isBlank()) {
            this.client = null;
            LOGGER.log(System.Logger.Level.WARNING,
                "Address geocoder is not configured. Set ROADASSIST_GEOCODER_BASE_URL before starting the backend; address lookups will return HTTP 503. No address or endpoint value is logged.");
            return;
        }

        URI endpoint;
        try {
            endpoint = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Geocoder URL is invalid; configure an absolute private service URL.");
        }
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("Geocoder URL must be an absolute URL without embedded credentials");
        }
        if (PUBLIC_NOMINATIM_HOST.equalsIgnoreCase(endpoint.getHost())) {
            throw new IllegalArgumentException(
                "Do not send private breakdown addresses to public Nominatim; configure a private endpoint.");
        }
        boolean localHttp = "http".equalsIgnoreCase(endpoint.getScheme())
            && ("localhost".equalsIgnoreCase(endpoint.getHost())
                || "127.0.0.1".equals(endpoint.getHost())
                || "::1".equals(endpoint.getHost()));
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !localHttp) {
            throw new IllegalArgumentException("Remote geocoder URLs must use HTTPS");
        }
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
            .baseUrl(endpoint.toString())
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public synchronized Optional<GeocodedLocation> geocode(String address) {
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Address lookup is not configured. Contact the service administrator.");
        }
        waitForRequestSlot();
        try {
            List<Map<String, Object>> results = client.get()
                .uri(uriBuilder -> uriBuilder.path("/search")
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .queryParam("q", address)
                    .build())
                .header(HttpHeaders.USER_AGENT, userAgent)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
            if (results == null || results.isEmpty()) return Optional.empty();
            Map<String, Object> first = results.get(0);
            if (first == null || first.get("lat") == null || first.get("lon") == null) {
                throw unavailable();
            }
            BigDecimal latitude = new BigDecimal(String.valueOf(first.get("lat")));
            BigDecimal longitude = new BigDecimal(String.valueOf(first.get("lon")));
            return Optional.of(new GeocodedLocation(latitude, longitude));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException | NumberFormatException exception) {
            throw unavailable();
        }
    }

    @Override
    public boolean isRealAddressLookupConfigured() {
        return client != null;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Address lookup is temporarily unavailable. Try again later.");
    }

    private void waitForRequestSlot() {
        long waitNanos = nextRequestAtNanos - System.nanoTime();
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address lookup was interrupted. Try again.");
            }
        }
        nextRequestAtNanos = System.nanoTime() + minIntervalNanos;
    }
}
