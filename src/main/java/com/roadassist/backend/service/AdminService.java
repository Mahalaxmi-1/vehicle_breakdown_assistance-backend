package com.roadassist.backend.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadassist.backend.dto.AdminCategoryCreateRequest;
import com.roadassist.backend.dto.AdminRequestResponse;
import com.roadassist.backend.dto.AdminUserResponse;
import com.roadassist.backend.dto.ServiceCategoryResponse;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.ServiceCategory;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.FeedbackRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;
import com.roadassist.backend.repository.UserRepository;

@Service
public class AdminService {
    private final UserRepository users;
    private final AssistanceRequestRepository requests;
    private final RequestStatusHistoryRepository history;
    private final ServiceCategoryRepository categories;
    private final FeedbackRepository feedback;

    public AdminService(UserRepository users, AssistanceRequestRepository requests,
            RequestStatusHistoryRepository history, ServiceCategoryRepository categories,
            FeedbackRepository feedback) {
        this.users = users;
        this.requests = requests;
        this.history = history;
        this.categories = categories;
        this.feedback = feedback;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> users(int page, int size, User.Role role) {
        Page<User> result = role == null
            ? users.findAll(page(page, size))
            : users.findAllByRole(role, page(page, size));
        return result.map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse setActive(Long id, boolean active, String adminEmail) {
        User target = users.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getEmail().equalsIgnoreCase(adminEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrators cannot deactivate their own account");
        }
        target.setActive(active);
        return AdminUserResponse.from(users.save(target));
    }

    @Transactional(readOnly = true)
    public Page<AdminRequestResponse> requests(int page, int size) {
        return requests.findAll(page(page, size)).map(AdminRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<com.roadassist.backend.dto.RequestStatusHistoryResponse> history(int page, int size) {
        return history.findAll(page(page, size))
            .map(com.roadassist.backend.dto.RequestStatusHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<com.roadassist.backend.dto.FeedbackResponse> feedback(int page, int size) {
        return feedback.findAll(page(page, size)).map(com.roadassist.backend.dto.FeedbackResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ServiceCategoryResponse> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(ServiceCategoryResponse::from).toList();
    }

    @Transactional
    public ServiceCategoryResponse createCategory(AdminCategoryCreateRequest input) {
        if (categories.findByNameIgnoreCase(input.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists");
        }
        return ServiceCategoryResponse.from(categories.saveAndFlush(
            new ServiceCategory(input.name(), input.description())));
    }

    @Transactional
    public ServiceCategoryResponse setCategoryActive(Long id, boolean active) {
        ServiceCategory category = categories.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.setActive(active);
        return ServiceCategoryResponse.from(categories.save(category));
    }

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        return new AdminOverview(users.count(), users.countByActiveTrue(),
            requests.count(), requests.countByStatus(AssistanceRequest.Status.REQUESTED),
            requests.countByStatus(AssistanceRequest.Status.COMPLETED), feedback.count());
    }

    private PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)),
            Sort.by(Sort.Direction.DESC, "id"));
    }

    public record AdminOverview(long users, long activeUsers, long requests,
            long requested, long completed, long feedback) { }
}
