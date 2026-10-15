package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.AdminCategoryCreateRequest;
import com.roadassist.backend.dto.AdminUserResponse;
import com.roadassist.backend.dto.ServiceCategoryResponse;
import com.roadassist.backend.model.ServiceCategory;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.FeedbackRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.AdminService;

class AdminServiceTests {

    private UserRepository users;
    private AssistanceRequestRepository requests;
    private RequestStatusHistoryRepository history;
    private ServiceCategoryRepository categories;
    private FeedbackRepository feedback;
    private AdminService adminService;

    private User admin;
    private User mechanic;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        requests = mock(AssistanceRequestRepository.class);
        history = mock(RequestStatusHistoryRepository.class);
        categories = mock(ServiceCategoryRepository.class);
        feedback = mock(FeedbackRepository.class);

        adminService = new AdminService(users, requests, history, categories, feedback);

        admin = new User();
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.setFullName("System Admin");
        admin.setEmail("admin@roadassist.test");
        admin.setPhone("9000000000");
        admin.setRole(User.Role.ADMIN);
        admin.setActive(true);

        mechanic = new User();
        ReflectionTestUtils.setField(mechanic, "id", 2L);
        mechanic.setFullName("John Mechanic");
        mechanic.setEmail("mechanic@roadassist.test");
        mechanic.setPhone("9111111111");
        mechanic.setRole(User.Role.MECHANIC);
        mechanic.setActive(true);
    }

    @Test
    void adminCannotDeactivateTheirOwnAccount() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            adminService.setActive(1L, false, "admin@roadassist.test"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Administrators cannot deactivate their own account", ex.getReason());
        verify(users, never()).save(any());
    }

    @Test
    void adminCanDeactivateOtherUserAccount() {
        when(users.findById(2L)).thenReturn(Optional.of(mechanic));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = adminService.setActive(2L, false, "admin@roadassist.test");

        assertNotNull(response);
        assertFalse(response.active());
        verify(users).save(mechanic);
    }

    @Test
    void setInactiveUserNotFoundThrowsNotFound() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            adminService.setActive(99L, false, "admin@roadassist.test"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void duplicateCategoryCreationThrowsConflict() {
        when(categories.findByNameIgnoreCase("Battery")).thenReturn(Optional.of(new ServiceCategory("Battery", "Existing")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            adminService.createCategory(new AdminCategoryCreateRequest("Battery", "New battery service")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Category already exists", ex.getReason());
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void uniqueCategoryCreationSucceeds() {
        when(categories.findByNameIgnoreCase("Brake Repair")).thenReturn(Optional.empty());
        ServiceCategory saved = new ServiceCategory("Brake Repair", "Fixing brakes");
        ReflectionTestUtils.setField(saved, "id", 10L);
        when(categories.saveAndFlush(any(ServiceCategory.class))).thenReturn(saved);

        ServiceCategoryResponse response = adminService.createCategory(
            new AdminCategoryCreateRequest("Brake Repair", "Fixing brakes"));

        assertNotNull(response);
        assertEquals("Brake Repair", response.name());
        verify(categories).saveAndFlush(any(ServiceCategory.class));
    }

    @Test
    void setCategoryActiveTogglesStatus() {
        ServiceCategory category = new ServiceCategory("Towing", "Vehicle towing");
        ReflectionTestUtils.setField(category, "id", 5L);
        category.setActive(true);
        when(categories.findById(5L)).thenReturn(Optional.of(category));
        when(categories.save(any(ServiceCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceCategoryResponse response = adminService.setCategoryActive(5L, false);

        assertNotNull(response);
        assertFalse(response.active());
    }
}
