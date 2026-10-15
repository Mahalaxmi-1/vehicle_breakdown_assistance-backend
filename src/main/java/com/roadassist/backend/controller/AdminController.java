package com.roadassist.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roadassist.backend.dto.AdminActiveUpdateRequest;
import com.roadassist.backend.dto.AdminCategoryCreateRequest;
import com.roadassist.backend.dto.AdminUserResponse;
import com.roadassist.backend.dto.ServiceCategoryResponse;
import com.roadassist.backend.service.AdminService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) { this.adminService = adminService; }

    @GetMapping("/overview")
    public AdminService.AdminOverview overview() { return adminService.overview(); }

    @GetMapping("/users")
    public Page<AdminUserResponse> users(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return adminService.users(page, size, null);
    }

    @GetMapping("/mechanics")
    public Page<AdminUserResponse> mechanics(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return adminService.users(page, size, com.roadassist.backend.model.User.Role.MECHANIC);
    }

    @PatchMapping("/users/{id}/active")
    public AdminUserResponse setActive(@PathVariable Long id,
            @Valid @RequestBody AdminActiveUpdateRequest input,
            @AuthenticationPrincipal UserDetails principal) {
        return adminService.setActive(id, input.active(), principal.getUsername());
    }

    @GetMapping("/requests")
    public Page<com.roadassist.backend.dto.AdminRequestResponse> requests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return adminService.requests(page, size);
    }

    @GetMapping("/history")
    public Page<com.roadassist.backend.dto.RequestStatusHistoryResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return adminService.history(page, size);
    }

    @GetMapping("/feedback")
    public Page<com.roadassist.backend.dto.FeedbackResponse> feedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return adminService.feedback(page, size);
    }

    @GetMapping("/categories")
    public java.util.List<ServiceCategoryResponse> categories() {
        return adminService.categories();
    }

    @PostMapping("/categories")
    public ServiceCategoryResponse createCategory(@Valid @RequestBody AdminCategoryCreateRequest input) {
        return adminService.createCategory(input);
    }

    @PatchMapping("/categories/{id}/active")
    public ServiceCategoryResponse setCategoryActive(@PathVariable Long id,
            @Valid @RequestBody AdminActiveUpdateRequest input) {
        return adminService.setCategoryActive(id, input.active());
    }
}
