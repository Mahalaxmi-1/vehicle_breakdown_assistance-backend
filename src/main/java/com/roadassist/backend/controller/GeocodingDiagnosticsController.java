package com.roadassist.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.service.geocoding.GeocodingRuntimeDiagnostics;
import com.roadassist.backend.service.geocoding.GeocodingRuntimeDiagnostics.Status;

/** Admin-only view of non-secret geocoder runtime state. */
@RestController
@RequestMapping("/api/admin/diagnostics")
public class GeocodingDiagnosticsController {
    private final GeocodingRuntimeDiagnostics diagnostics;

    public GeocodingDiagnosticsController(GeocodingRuntimeDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping("/geocoding")
    public Status geocodingStatus() {
        return diagnostics.status();
    }
}
