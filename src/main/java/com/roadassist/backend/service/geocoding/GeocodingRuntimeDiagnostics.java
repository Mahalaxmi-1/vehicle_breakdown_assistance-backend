package com.roadassist.backend.service.geocoding;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Exposes only non-secret geocoder runtime state for startup logs and admin diagnostics. */
@Component
public class GeocodingRuntimeDiagnostics {
    private static final System.Logger LOGGER = System.getLogger(GeocodingRuntimeDiagnostics.class.getName());

    public record Status(String providerImplementation, boolean realAddressLookupConfigured,
            List<String> activeProfiles) { }

    private final GeocodingProvider provider;
    private final Environment environment;

    public GeocodingRuntimeDiagnostics(GeocodingProvider provider, Environment environment) {
        this.provider = provider;
        this.environment = environment;
    }

    public Status status() {
        return new Status(provider.getClass().getSimpleName(),
            provider.isRealAddressLookupConfigured(), Arrays.asList(environment.getActiveProfiles()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logRuntimeStatus() {
        Status status = status();
        LOGGER.log(System.Logger.Level.INFO,
            "Geocoding runtime: provider={0}, realAddressLookupConfigured={1}, activeProfiles={2}. Endpoint and address values are not logged.",
            status.providerImplementation(), status.realAddressLookupConfigured(), status.activeProfiles());
    }
}
