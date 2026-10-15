package com.roadassist.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.VehicleRequest;
import com.roadassist.backend.dto.VehicleResponse;
import com.roadassist.backend.service.VehicleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/owner/vehicles")
public class VehicleController {
    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return vehicleService.list(principal.getUsername());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse create(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(principal.getUsername(), request);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@AuthenticationPrincipal UserDetails principal,
            @PathVariable long id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(principal.getUsername(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal UserDetails principal, @PathVariable long id) {
        vehicleService.archive(principal.getUsername(), id);
    }
}
