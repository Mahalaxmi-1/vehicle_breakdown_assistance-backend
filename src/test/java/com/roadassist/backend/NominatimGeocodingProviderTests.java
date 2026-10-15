package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.roadassist.backend.service.geocoding.NominatimGeocodingProvider;
import com.roadassist.backend.service.geocoding.MockGeocodingProvider;

class NominatimGeocodingProviderTests {
    @Test
    void configuredPrivateNominatimEndpointResolvesAddressWithIdentifyingUserAgent() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> agent = new AtomicReference<>();
        server.createContext("/search", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            agent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] response = "[{\"lat\":\"17.44\",\"lon\":\"78.39\"}]"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        server.start();
        try {
            var provider = new NominatimGeocodingProvider(
                "http://127.0.0.1:" + server.getAddress().getPort(), "RoadAssist-test/1.0", 1000);

            var result = provider.geocode("Madhapur, Hyderabad").orElseThrow();

            assertEquals("17.44", result.latitude().toPlainString());
            assertEquals("78.39", result.longitude().toPlainString());
            assertEquals("RoadAssist-test/1.0", agent.get());
            org.junit.jupiter.api.Assertions.assertTrue(query.get().contains("format=jsonv2"));
            org.junit.jupiter.api.Assertions.assertTrue(query.get().contains("limit=1"));
            org.junit.jupiter.api.Assertions.assertTrue(query.get().contains("Madhapur"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void noEndpointReturnsSafeUnavailableErrorAndRejectsPublicNominatim() {
        var disabled = new NominatimGeocodingProvider("", "RoadAssist-test/1.0", 1000);
        ResponseStatusException unavailable = assertThrows(ResponseStatusException.class,
            () -> disabled.geocode("Madhapur, Hyderabad"));
        assertEquals(503, unavailable.getStatusCode().value());

        assertThrows(IllegalArgumentException.class, () -> new NominatimGeocodingProvider(
            "https://nominatim.openstreetmap.org", "RoadAssist-test/1.0", 1000));
    }

    @Test
    void springCanInstantiateProviderWithoutRestClientBuilderBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            // The full suite may set mock-geocoder globally; this focused context
            // intentionally selects the real-provider profile to test its bean.
            context.getEnvironment().setActiveProfiles("nominatim-provider-test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "geocoder-test", Map.of("roadassist.geocoding.base-url", "")));
            context.register(NominatimGeocodingProvider.class);
            context.refresh();

            org.junit.jupiter.api.Assertions.assertNotNull(
                context.getBean(NominatimGeocodingProvider.class));
        }
    }

    @Test
    void emptyProviderResultIsReportedAsNotFoundByService() throws IOException {
        assertEquals(java.util.Optional.empty(), invokeWithResponse("[]", 200));
    }

    @Test
    void httpFailureReturnsSafeUnavailableMessage() throws IOException {
        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class, () -> invokeWithResponse("unavailable", 502));
        assertEquals(503, exception.getStatusCode().value());
        assertEquals("Address lookup is temporarily unavailable. Try again later.", exception.getReason());
    }

    @Test
    void malformedProviderResponseReturnsSafeUnavailableMessage() throws IOException {
        for (String body : new String[] { "not-json", "[null]", "[{\"lat\":null,\"lon\":\"78.39\"}]" }) {
            ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> invokeWithResponse(body, 200));
            assertEquals(503, exception.getStatusCode().value());
            assertEquals("Address lookup is temporarily unavailable. Try again later.", exception.getReason());
        }
    }

    @Test
    void providerTimeoutReturnsSafeUnavailableMessage() throws IOException, InterruptedException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch requestArrived = new CountDownLatch(1);
        server.createContext("/search", exchange -> {
            requestArrived.countDown();
            try {
                Thread.sleep(6000);
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The provider should close its timed-out request before this delayed response.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var provider = providerFor(server);
            ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> provider.geocode("Synthetic timeout test"));
            assertEquals(503, exception.getStatusCode().value());
            assertEquals("Address lookup is temporarily unavailable. Try again later.", exception.getReason());
            org.junit.jupiter.api.Assertions.assertTrue(requestArrived.await(1, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mockProviderOnlyResolvesItsExplicitSyntheticLocation() {
        var mock = new MockGeocodingProvider();
        assertEquals("17.440000", mock.geocode(MockGeocodingProvider.SYNTHETIC_ADDRESS)
            .orElseThrow().latitude().toPlainString());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> mock.geocode("A real customer address"));
        assertEquals(503, exception.getStatusCode().value());
    }

    @Test
    void mockProfileSelectsOnlyTheSyntheticProvider() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("mock-geocoder");
            context.register(NominatimGeocodingProvider.class, MockGeocodingProvider.class);
            context.refresh();

            org.junit.jupiter.api.Assertions.assertInstanceOf(MockGeocodingProvider.class,
                context.getBean(com.roadassist.backend.service.geocoding.GeocodingProvider.class));
        }
    }

    private static java.util.Optional<com.roadassist.backend.service.geocoding.GeocodedLocation>
            invokeWithResponse(String body, int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            return providerFor(server).geocode("Synthetic provider test");
        } finally {
            server.stop(0);
        }
    }

    private static NominatimGeocodingProvider providerFor(HttpServer server) {
        return new NominatimGeocodingProvider("http://127.0.0.1:" + server.getAddress().getPort(),
            "RoadAssist-test/1.0", 1000);
    }
}
