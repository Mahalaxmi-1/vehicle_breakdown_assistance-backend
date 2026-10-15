package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.RequestStatusHistory;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;

@SpringBootTest
@AutoConfigureMockMvc
class MechanicAcceptanceConcurrencyTests {
    private static final String PASSWORD = "test-password-21";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private AssistanceRequestRepository requestRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private RequestStatusHistoryRepository historyRepository;

    private User owner;
    private User firstMechanic;
    private User secondMechanic;
    private Vehicle vehicle;
    private AssistanceRequest request;

    @BeforeEach
    void createCommittedFixture() {
        String suffix = UUID.randomUUID().toString();
        owner = user("race-owner-" + suffix + "@example.test", User.Role.OWNER);
        firstMechanic = user("race-mechanic-a-" + suffix + "@example.test", User.Role.MECHANIC);
        secondMechanic = user("race-mechanic-b-" + suffix + "@example.test", User.Role.MECHANIC);
        firstMechanic.setAvailability(User.Availability.AVAILABLE);
        secondMechanic.setAvailability(User.Availability.AVAILABLE);
        owner = userRepository.saveAndFlush(owner);
        firstMechanic = userRepository.saveAndFlush(firstMechanic);
        secondMechanic = userRepository.saveAndFlush(secondMechanic);
        vehicle = vehicleRepository.saveAndFlush(new Vehicle(owner,
            "RACE" + suffix.substring(0, 8).replace("-", ""), "Car", "Test", "RoadAssist"));
        request = requestRepository.saveAndFlush(new AssistanceRequest(owner, vehicle,
            "Battery", "Vehicle does not start after a battery failure.", "Madhapur, Hyderabad",
            new BigDecimal("17.440000"), new BigDecimal("78.390000")));
    }

    @AfterEach
    void removeCommittedFixture() {
        if (request != null) {
            historyRepository.deleteAll(historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId()));
            requestRepository.deleteById(request.getId());
        }
        if (vehicle != null) vehicleRepository.deleteById(vehicle.getId());
        if (owner != null) userRepository.deleteById(owner.getId());
        if (firstMechanic != null) userRepository.deleteById(firstMechanic.getId());
        if (secondMechanic != null) userRepository.deleteById(secondMechanic.getId());
    }

    @Test
    void simultaneousMechanicClaimsAssignOnlyOneMechanic() throws Exception {
        MockHttpSession firstSession = login(firstMechanic.getEmail());
        MockHttpSession secondSession = login(secondMechanic.getEmail());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> acceptAfter(start, firstSession));
            Future<Integer> second = executor.submit(() -> acceptAfter(start, secondSession));
            start.countDown();
            List<Integer> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 409).count());
            AssistanceRequest stored = requestRepository.findById(request.getId()).orElseThrow();
            assertEquals(AssistanceRequest.Status.ACCEPTED, stored.getStatus());
            assertNotNull(stored.getAssignedMechanic());
            assertEquals(1, historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId()).size());
            assertEquals(1, List.of(firstMechanic, secondMechanic).stream()
                .filter(mechanic -> userRepository.findById(mechanic.getId()).orElseThrow()
                    .getAvailability() == User.Availability.BUSY).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void availabilityChangeRacingWithAcceptanceLeavesSerializableState() throws Exception {
        MockHttpSession session = login(firstMechanic.getEmail());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> change = executor.submit(() -> {
                start.await();
                return mockMvc.perform(withCsrf(put("/api/mechanic/availability"), session)
                        .contentType("application/json").content("{\"availability\":\"OFFLINE\"}"))
                    .andReturn().getResponse().getStatus();
            });
            Future<Integer> accept = executor.submit(() -> acceptAfter(start, session));
            start.countDown();
            change.get();
            accept.get();
            User storedMechanic = userRepository.findById(firstMechanic.getId()).orElseThrow();
            AssistanceRequest storedRequest = requestRepository.findById(request.getId()).orElseThrow();
            assertEquals(User.Availability.OFFLINE, storedMechanic.getAvailability());
            if (storedRequest.getStatus() == AssistanceRequest.Status.REQUESTED) {
                assertNull(storedRequest.getAssignedMechanic());
                assertEquals(0, historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId()).size());
            } else {
                assertEquals(AssistanceRequest.Status.ACCEPTED, storedRequest.getStatus());
                assertEquals(firstMechanic.getId(), storedRequest.getAssignedMechanic().getId());
                assertEquals(1, historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId()).size());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void statusHistoryFailureRollsBackClaimAndAvailability() throws Exception {
        doThrow(new IllegalStateException("test history failure"))
            .when(historyRepository).saveAndFlush(any(RequestStatusHistory.class));
        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", request.getId()),
                login(firstMechanic.getEmail())))
            .andExpect(status().isInternalServerError());
        AssistanceRequest storedRequest = requestRepository.findById(request.getId()).orElseThrow();
        User storedMechanic = userRepository.findById(firstMechanic.getId()).orElseThrow();
        assertEquals(AssistanceRequest.Status.REQUESTED, storedRequest.getStatus());
        assertNull(storedRequest.getAssignedMechanic());
        assertEquals(User.Availability.AVAILABLE, storedMechanic.getAvailability());
        assertEquals(0, historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId()).size());
    }

    private int acceptAfter(CountDownLatch start, MockHttpSession session) throws Exception {
        start.await();
        return mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", request.getId()), session))
            .andReturn().getResponse().getStatus();
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        MvcResult result = mockMvc.perform(post("/api/auth/login").cookie(cookie)
                .header("X-XSRF-TOKEN", token).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                    + "\",\"role\":\"MECHANIC\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return request.session(session).cookie(result.getResponse().getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN", token);
    }

    private User user(String email, User.Role role) {
        User user = new User();
        user.setFullName("Concurrency Test");
        user.setEmail(email);
        user.setPhone("9876543210");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        if (role == User.Role.MECHANIC) {
            user.setSpecialization("Battery");
            user.setServiceLocation("Hyderabad");
            user.setMechanicLatitude(new BigDecimal("17.440000"));
            user.setMechanicLongitude(new BigDecimal("78.390000"));
            user.setAvailability(User.Availability.OFFLINE);
        }
        return user;
    }
}
