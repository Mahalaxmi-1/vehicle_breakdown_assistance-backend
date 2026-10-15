package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.math.BigDecimal;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.repository.FeedbackRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MechanicRequestTests {
    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private AssistanceRequestRepository requestRepository;
    @Autowired private RequestStatusHistoryRepository historyRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String ownerEmail;
    private String mechanicEmail;
    private String secondMechanicEmail;
    private Long requestId;
    private Long mechanicId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        ownerEmail = "mechanic-owner-" + suffix + "@example.test";
        mechanicEmail = "mechanic-a-" + suffix + "@example.test";
        secondMechanicEmail = "mechanic-b-" + suffix + "@example.test";
        User owner = userRepository.saveAndFlush(account(ownerEmail, User.Role.OWNER, true));
        User mechanic = userRepository.saveAndFlush(account(mechanicEmail, User.Role.MECHANIC, true));
        User secondMechanic = account(secondMechanicEmail, User.Role.MECHANIC, true);
        secondMechanic.setAvailability(User.Availability.AVAILABLE);
        userRepository.saveAndFlush(secondMechanic);
        mechanic.setAvailability(User.Availability.AVAILABLE);
        userRepository.saveAndFlush(mechanic);
        mechanicId = mechanic.getId();
        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle(owner,
            "TS" + suffix.substring(0, 8), "Car", "Test", "RoadAssist"));
        requestId = requestRepository.saveAndFlush(new AssistanceRequest(owner, vehicle,
            "Battery", "Vehicle does not start after a battery failure.", "Madhapur, Hyderabad",
            new BigDecimal("17.440000"), new BigDecimal("78.390000")))
            .getId();
    }

    @Test
    void unauthenticatedUsersCannotViewMechanicRequests() throws Exception {
        mockMvc.perform(get("/api/mechanic/requests"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCannotAccessMechanicEndpoints() throws Exception {
        mockMvc.perform(get("/api/mechanic/requests").session(login(ownerEmail, "OWNER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void inactiveMechanicCannotAccessRequestsEvenWithMechanicAuthority() throws Exception {
        User mechanic = userRepository.findByEmailIgnoreCase(mechanicEmail).orElseThrow();
        mechanic.setActive(false);
        userRepository.saveAndFlush(mechanic);

        mockMvc.perform(get("/api/mechanic/requests").with(user(mechanicEmail).roles("MECHANIC")))
            .andExpect(status().isForbidden());
    }

    @Test
    void mechanicSeesAvailableRequestsWithoutSensitiveOwnerFields() throws Exception {
        mockMvc.perform(get("/api/mechanic/requests").session(login(mechanicEmail, "MECHANIC")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(requestId))
            .andExpect(jsonPath("$[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$[0].vehicleNumber").exists())
            .andExpect(jsonPath("$[0].description").exists())
            .andExpect(jsonPath("$[0].owner").doesNotExist())
            .andExpect(jsonPath("$[0].password").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist());
    }

    @Test
    void acceptanceAssignsAuthenticatedMechanicAndWritesHistory() throws Exception {
        MockHttpSession session = login(mechanicEmail, "MECHANIC");
        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", requestId), session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(requestId))
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.assignedMechanic").doesNotExist());

        AssistanceRequest saved = requestRepository.findById(requestId).orElseThrow();
        assertEquals(AssistanceRequest.Status.ACCEPTED, saved.getStatus());
        assertEquals(mechanicId, saved.getAssignedMechanic().getId());
        assertEquals(User.Availability.BUSY,
            userRepository.findById(mechanicId).orElseThrow().getAvailability());
        var history = historyRepository.findAllByRequestIdOrderByChangedAtAsc(requestId);
        assertEquals(1, history.size());
        assertEquals(AssistanceRequest.Status.REQUESTED, history.get(0).getFromStatus());
        assertEquals(AssistanceRequest.Status.ACCEPTED, history.get(0).getToStatus());
        assertEquals(mechanicId, history.get(0).getChangedBy().getId());
    }

    @Test
    void onlyOneMechanicCanAcceptARequest() throws Exception {
        MockHttpSession firstMechanic = login(mechanicEmail, "MECHANIC");
        MockHttpSession secondMechanic = login(secondMechanicEmail, "MECHANIC");

        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", requestId), firstMechanic))
            .andExpect(status().isOk());
        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", requestId), secondMechanic))
            .andExpect(status().isConflict());

        AssistanceRequest saved = requestRepository.findById(requestId).orElseThrow();
        assertEquals(mechanicId, saved.getAssignedMechanic().getId());
        assertEquals(1, historyRepository.findAllByRequestIdOrderByChangedAtAsc(requestId).size());
    }

    @Test
    void assignedRequestsAreIsolatedToTheAuthenticatedMechanic() throws Exception {
        MockHttpSession firstMechanic = login(mechanicEmail, "MECHANIC");
        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", requestId), firstMechanic))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/mechanic/requests/mine").session(login(secondMechanicEmail, "MECHANIC")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/mechanic/requests/mine").session(firstMechanic))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(requestId))
            .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    void invalidRequestIdsDoNotChangeAnyAssignment() throws Exception {
        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", Long.MAX_VALUE),
                login(mechanicEmail, "MECHANIC")))
            .andExpect(status().isNotFound());
        AssistanceRequest saved = requestRepository.findById(requestId).orElseThrow();
        assertEquals(AssistanceRequest.Status.REQUESTED, saved.getStatus());
        assertNull(saved.getAssignedMechanic());
    }

    @Test
    void newMechanicsDefaultOfflineAndOnlyMechanicCanReadOrUpdateOwnAvailability() throws Exception {
        User created = account("new-mechanic-" + UUID.randomUUID() + "@example.test",
            User.Role.MECHANIC, true);
        created.setAvailability(null);
        created = userRepository.saveAndFlush(created);
        assertEquals(User.Availability.OFFLINE, created.getAvailability());
        assertNull(userRepository.findByEmailIgnoreCase(ownerEmail).orElseThrow().getAvailability());
        User admin = userRepository.saveAndFlush(account(
            "test-admin-" + UUID.randomUUID() + "@example.test", User.Role.ADMIN, true));
        assertNull(admin.getAvailability());

        mockMvc.perform(get("/api/mechanic/availability"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mechanic/availability")
                .session(login(ownerEmail, "OWNER")))
            .andExpect(status().isForbidden());

        MockHttpSession session = login(mechanicEmail, "MECHANIC");
        mockMvc.perform(get("/api/mechanic/availability").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availability").value("AVAILABLE"))
            .andExpect(jsonPath("$.id").doesNotExist());
        mockMvc.perform(withCsrf(put("/api/mechanic/availability"), session)
                .contentType("application/json")
                .content("{\"availability\":\"OFFLINE\",\"mechanicId\":999999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availability").value("OFFLINE"));
        assertEquals(User.Availability.OFFLINE,
            userRepository.findById(mechanicId).orElseThrow().getAvailability());
    }

    @Test
    void busyAndOfflineMechanicsCannotAcceptRequests() throws Exception {
        User mechanic = userRepository.findById(mechanicId).orElseThrow();
        for (User.Availability state : new User.Availability[] {
                User.Availability.BUSY, User.Availability.OFFLINE }) {
            mechanic.setAvailability(state);
            userRepository.saveAndFlush(mechanic);
            mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", requestId),
                    login(mechanicEmail, "MECHANIC")))
                .andExpect(status().isConflict());
        }
        assertEquals(AssistanceRequest.Status.REQUESTED,
            requestRepository.findById(requestId).orElseThrow().getStatus());
    }

    @Test
    void invalidAvailabilityValueIsRejected() throws Exception {
        MockHttpSession session = login(mechanicEmail, "MECHANIC");
        mockMvc.perform(withCsrf(put("/api/mechanic/availability"), session)
                .contentType("application/json")
                .content("{\"availability\":\"READY\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void assignedMechanicCanAdvanceStatusesAndOwnerCanReviewCompletedRequestOnce() throws Exception {
        MockHttpSession mechanicSession = login(mechanicEmail, "MECHANIC");
        mockMvc.perform(withCsrf(post("/api/mechanic/requests/{id}/accept", requestId), mechanicSession))
            .andExpect(status().isOk());
        for (String next : new String[] { "ON_THE_WAY", "IN_SERVICE" }) {
            mockMvc.perform(withCsrf(patch("/api/mechanic/requests/{id}/status", requestId), mechanicSession)
                    .contentType("application/json")
                    .content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(next));
        }
        mockMvc.perform(withCsrf(patch("/api/mechanic/requests/{id}/status", requestId), mechanicSession)
                .contentType("application/json")
                .content("{\"status\":\"COMPLETED\",\"notes\":\"Battery tested and vehicle started.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceDetails").value("Battery tested and vehicle started."));

        MockHttpSession ownerSession = login(ownerEmail, "OWNER");
        mockMvc.perform(withCsrf(post("/api/owner/feedback/{id}", requestId), ownerSession)
                .contentType("application/json")
                .content("{\"rating\":5,\"comment\":\"Quick and helpful roadside assistance.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rating").value(5))
            .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(withCsrf(post("/api/owner/feedback/{id}", requestId), ownerSession)
                .contentType("application/json")
                .content("{\"rating\":5,\"comment\":\"Duplicate review attempt.\"}"))
            .andExpect(status().isConflict());

        assertEquals(4, historyRepository.findAllByRequestIdOrderByChangedAtAsc(requestId).size());
        assertEquals(User.Availability.BUSY,
            userRepository.findById(mechanicId).orElseThrow().getAvailability());
        assertEquals(true, feedbackRepository.existsByRequestId(requestId));
    }

    private MockHttpSession login(String email, String role) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .cookie(cookie).header("X-XSRF-TOKEN", token)
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                    + "\",\"role\":\"" + role + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            MockHttpSession session) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        return request.session(session)
            .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN", token);
    }

    private User account(String email, User.Role role, boolean active) {
        User user = new User();
        user.setFullName("Test Account");
        user.setEmail(email);
        user.setPhone("9876543210");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(active);
        if (role == User.Role.MECHANIC) {
            user.setSpecialization("General");
            user.setServiceLocation("Hyderabad");
            user.setMechanicLatitude(new BigDecimal("17.440000"));
            user.setMechanicLongitude(new BigDecimal("78.390000"));
            user.setAvailability(User.Availability.OFFLINE);
        }
        return user;
    }
}
