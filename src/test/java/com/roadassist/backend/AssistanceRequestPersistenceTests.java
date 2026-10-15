package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.roadassist.backend.model.AssistanceRequest;
import com.roadassist.backend.model.RequestStatusHistory;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.AssistanceRequestRepository;
import com.roadassist.backend.repository.RequestStatusHistoryRepository;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;
import com.roadassist.backend.repository.ServiceCategoryRepository;
import com.roadassist.backend.model.ServiceCategory;
import com.roadassist.backend.service.AddressGeocodingService;
import com.roadassist.backend.service.geocoding.GeocodedLocation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssistanceRequestPersistenceTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssistanceRequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ServiceCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private RequestStatusHistoryRepository historyRepository;
    @MockitoBean private AddressGeocodingService geocodingService;

    private String ownerEmail;
    private String otherOwnerEmail;
    private Long ownerId;
    private Long otherOwnerId;
    private Long ownerVehicleId;
    private Long otherVehicleId;

    @BeforeEach
    void createIsolatedAccountsAndVehicles() {
        org.mockito.Mockito.when(geocodingService.resolve(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new GeocodedLocation(new BigDecimal("17.440000"),
                new BigDecimal("78.390000")));
        for (String category : new String[] { "Battery", "Tyre", "Engine", "Fuel", "Electrical", "Other" }) {
            categoryRepository.findByNameIgnoreCase(category).orElseGet(() ->
                categoryRepository.saveAndFlush(new ServiceCategory(category, "Test category")));
        }
        String suffix = UUID.randomUUID().toString();
        ownerEmail = "request-owner-" + suffix + "@example.test";
        otherOwnerEmail = "request-other-" + suffix + "@example.test";
        ownerId = userRepository.saveAndFlush(account(ownerEmail)).getId();
        otherOwnerId = userRepository.saveAndFlush(account(otherOwnerEmail)).getId();
        ownerVehicleId = vehicleRepository.saveAndFlush(new Vehicle(
            userRepository.findById(ownerId).orElseThrow(), "AP09AB" + suffix.substring(0, 4),
            "Car", "Test", "Owner Car")).getId();
        otherVehicleId = vehicleRepository.saveAndFlush(new Vehicle(
            userRepository.findById(otherOwnerId).orElseThrow(), "AP10CD" + suffix.substring(4, 8),
            "Bike", "Test", "Other Car")).getId();
    }

    @Test
    void ownerCanCreateRequestAndInitialHistoryIsSaved() throws Exception {
        MockHttpSession session = login(ownerEmail);
        String payload = requestJson(ownerVehicleId, " Battery ", "  Engine will not start.  ", "  Madhapur, Hyderabad  ");
        payload = payload.substring(0, payload.length() - 1)
            + ",\"ownerId\":" + otherOwnerId
            + ",\"status\":\"COMPLETED\",\"assignedMechanicId\":" + otherOwnerId
            + ",\"latitude\":0,\"longitude\":0}";
        MvcResult result = mockMvc.perform(withCsrf(post("/api/owner/requests"), session)
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.problemCategory").value("Battery"))
            .andExpect(jsonPath("$.description").value("Engine will not start."))
            .andExpect(jsonPath("$.location").value("Madhapur, Hyderabad"))
            .andExpect(jsonPath("$.owner").doesNotExist())
            .andExpect(jsonPath("$.assignedMechanic").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        AssistanceRequest request = requestRepository.findById(id.longValue()).orElseThrow();
        assertEquals(ownerId, request.getOwner().getId());
        assertEquals(ownerVehicleId, request.getVehicle().getId());
        assertNull(request.getAssignedMechanic());
        assertEquals(new BigDecimal("17.440000"), request.getLatitude());
        assertEquals(new BigDecimal("78.390000"), request.getLongitude());

        var history = historyRepository.findAllByRequestIdOrderByChangedAtAsc(request.getId());
        assertEquals(1, history.size());
        assertNull(history.get(0).getFromStatus());
        assertEquals(AssistanceRequest.Status.REQUESTED, history.get(0).getToStatus());
        assertEquals(ownerId, history.get(0).getChangedBy().getId());
    }

    @Test
    void requestCoordinatesArePersistedAndReturnedToItsOwner() throws Exception {
        MockHttpSession session = login(ownerEmail);
        String payload = requestJson(ownerVehicleId, "Battery",
            "The vehicle battery will not start.", "Madhapur, Hyderabad");

        MvcResult result = mockMvc.perform(withCsrf(post("/api/owner/requests"), session)
                .contentType("application/json").content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.latitude").value(17.44))
            .andExpect(jsonPath("$.longitude").value(78.39))
            .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        AssistanceRequest request = requestRepository.findById(id.longValue()).orElseThrow();
        assertEquals(new BigDecimal("17.440000"), request.getLatitude());
        assertEquals(new BigDecimal("78.390000"), request.getLongitude());
    }

    @Test
    void unresolvedAddressIsRejectedWithoutSavingRequestOrHistory() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "We could not resolve that address. Add a nearby area, city, and state, then try again."))
            .when(geocodingService).resolve("Nowhere Road, Unknown City");

        mockMvc.perform(withCsrf(post("/api/owner/requests"), login(ownerEmail))
                .contentType("application/json")
                .content(requestJson(ownerVehicleId, "Battery",
                    "The vehicle battery will not start.", "Nowhere Road, Unknown City")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "We could not resolve that address. Add a nearby area, city, and state, then try again."));

        assertEquals(0, requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(ownerId).size());
        assertEquals(0, historyRepository.countByRequestOwnerId(ownerId));
    }

    @Test
    void invalidCategoryAndTrimmedFieldLengthsAreRejected() throws Exception {
        MockHttpSession session = login(ownerEmail);
        mockMvc.perform(withCsrf(post("/api/owner/requests"), session)
                .contentType("application/json")
                .content(requestJson(ownerVehicleId, "Wheels", "This description is long enough.", "Madhapur")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid request input"));

        mockMvc.perform(withCsrf(post("/api/owner/requests"), session)
                .contentType("application/json")
                .content(requestJson(ownerVehicleId, "Tyre", "   short   ", "  abc  ")))
            .andExpect(status().isBadRequest());

        mockMvc.perform(withCsrf(post("/api/owner/requests"), session)
                .contentType("application/json")
                .content(requestJson(ownerVehicleId, "Tyre", "x".repeat(1001), "Madhapur")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(withCsrf(post("/api/owner/requests"), session)
                .contentType("application/json")
                .content(requestJson(ownerVehicleId, "Tyre", "This description is long enough.", "x".repeat(301))))
            .andExpect(status().isBadRequest());
        assertEquals(0, requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(ownerId).size());
    }

    @Test
    void anotherOwnersVehicleCannotBeUsed() throws Exception {
        mockMvc.perform(withCsrf(post("/api/owner/requests"), login(ownerEmail))
                .contentType("application/json")
                .content(requestJson(otherVehicleId, "Battery", "The vehicle will not start.", "Madhapur")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Resource not found"));
        assertEquals(0, requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(ownerId).size());
    }

    @Test
    void archivedVehicleCannotBeUsed() throws Exception {
        Vehicle vehicle = vehicleRepository.findById(ownerVehicleId).orElseThrow();
        vehicle.setActive(false);
        vehicleRepository.saveAndFlush(vehicle);

        mockMvc.perform(withCsrf(post("/api/owner/requests"), login(ownerEmail))
                .contentType("application/json")
                .content(requestJson(ownerVehicleId, "Engine", "The vehicle will not start.", "Madhapur")))
            .andExpect(status().isNotFound());
        assertEquals(0, requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(ownerId).size());
    }

    @Test
    void ownerListingIsIsolatedByAuthenticatedAccount() throws Exception {
        submit(ownerEmail, ownerVehicleId, "Battery");
        submit(otherOwnerEmail, otherVehicleId, "Tyre");

        mockMvc.perform(get("/api/owner/requests").session(login(ownerEmail)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].problemCategory").value("Battery"));
        mockMvc.perform(get("/api/owner/requests").session(login(otherOwnerEmail)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].problemCategory").value("Tyre"))
            .andExpect(jsonPath("$[0].owner").doesNotExist());
    }

    @Test
    void unauthenticatedOwnerListingReturns401() throws Exception {
        mockMvc.perform(get("/api/owner/requests"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void historyFailureRollsBackRequestCreation() throws Exception {
        doThrow(new DataIntegrityViolationException("simulated history constraint failure"))
            .when(historyRepository).saveAndFlush(any(RequestStatusHistory.class));

        try {
            mockMvc.perform(withCsrf(post("/api/owner/requests"), login(ownerEmail))
                    .contentType("application/json")
                    .content(requestJson(ownerVehicleId, "Battery", "The vehicle will not start.", "Madhapur")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));

            assertEquals(0, requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(ownerId).size());
            assertEquals(0, historyRepository.countByRequestOwnerId(ownerId));
        } finally {
            removeTestFixtures();
        }
    }

    private void submit(String email, Long vehicleId, String category) throws Exception {
        mockMvc.perform(withCsrf(post("/api/owner/requests"), login(email))
                .contentType("application/json")
                .content(requestJson(vehicleId, category, "The vehicle will not start.", "Madhapur")))
            .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .cookie(cookie).header("X-XSRF-TOKEN", token)
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"role\":\"OWNER\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return request.session(session)
            .cookie(result.getResponse().getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN", token);
    }

    private User account(String email) {
        User user = new User();
        user.setFullName("Request Test Owner");
        user.setEmail(email);
        user.setPhone("9876543210");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(User.Role.OWNER);
        user.setActive(true);
        return user;
    }

    private String requestJson(Long vehicleId, String category, String description, String location) {
        return "{\"vehicleId\":" + vehicleId + ",\"problemCategory\":\"" + category
            + "\",\"description\":\"" + description + "\",\"location\":\"" + location + "\"}";
    }

    private void removeTestFixtures() {
        removeRequestsFor(ownerId);
        removeRequestsFor(otherOwnerId);
        vehicleRepository.findById(ownerVehicleId).ifPresent(vehicleRepository::delete);
        vehicleRepository.findById(otherVehicleId).ifPresent(vehicleRepository::delete);
        userRepository.findById(ownerId).ifPresent(userRepository::delete);
        userRepository.findById(otherOwnerId).ifPresent(userRepository::delete);
    }

    private void removeRequestsFor(Long testOwnerId) {
        requestRepository.findAllByOwnerIdOrderByRequestedAtDesc(testOwnerId).forEach(request -> {
            historyRepository.deleteAll(historyRepository
                .findAllByRequestIdOrderByChangedAtAsc(request.getId()));
            requestRepository.delete(request);
        });
    }
}
