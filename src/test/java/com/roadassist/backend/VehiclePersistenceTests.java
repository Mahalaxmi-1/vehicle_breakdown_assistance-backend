package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.roadassist.backend.model.User;
import com.roadassist.backend.model.Vehicle;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.repository.VehicleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VehiclePersistenceTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String ownerEmail;
    private String otherOwnerEmail;
    private Long ownerId;

    @BeforeEach
    void createOwnersWithinRollbackTransaction() {
        String suffix = UUID.randomUUID().toString();
        ownerEmail = "vehicle-owner-" + suffix + "@example.test";
        otherOwnerEmail = "vehicle-other-" + suffix + "@example.test";
        ownerId = userRepository.saveAndFlush(account(ownerEmail)).getId();
        userRepository.saveAndFlush(account(otherOwnerEmail));
    }

    @Test
    void ownerCanCreateReadUpdateAndArchivePersistedVehicle() throws Exception {
        MockHttpSession session = login(ownerEmail);
        String vehicleNumber = uniqueValidVehicleNumber();
        String spacedVehicleNumber = vehicleNumber.substring(0, 4) + " "
            + vehicleNumber.substring(4, 7) + " " + vehicleNumber.substring(7);
        String body = vehicleJson(spacedVehicleNumber, "Car", "Hyundai", "Creta");

        MvcResult created = mockMvc.perform(withCsrf(post("/api/owner/vehicles"), session)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.vehicleNumber").value(vehicleNumber))
            .andExpect(jsonPath("$.brand").value("Hyundai"))
            .andReturn();

        Number vehicleId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Vehicle persisted = vehicleRepository.findById(vehicleId.longValue()).orElseThrow();
        assertEquals(ownerId, persisted.getOwner().getId());

        mockMvc.perform(get("/api/owner/vehicles").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(vehicleId));

        mockMvc.perform(withCsrf(put("/api/owner/vehicles/{id}", vehicleId), session)
                .contentType("application/json")
                .content(vehicleJson(vehicleNumber, "Car", "Hyundai", "Venue")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.model").value("Venue"));
        assertEquals("Venue", vehicleRepository.findById(vehicleId.longValue()).orElseThrow().getModel());

        mockMvc.perform(withCsrf(delete("/api/owner/vehicles/{id}", vehicleId), session))
            .andExpect(status().isNoContent());
        assertFalse(vehicleRepository.findById(vehicleId.longValue()).orElseThrow().isActive());
        mockMvc.perform(get("/api/owner/vehicles").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void vehicleIsNotVisibleOrEditableByAnotherOwner() throws Exception {
        String vehicleNumber = uniqueValidVehicleNumber();
        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle(
            userRepository.findById(ownerId).orElseThrow(), vehicleNumber, "Bike", "Honda", "Activa"));
        MockHttpSession otherSession = login(otherOwnerEmail);

        mockMvc.perform(get("/api/owner/vehicles").session(otherSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(withCsrf(put("/api/owner/vehicles/{id}", vehicle.getId()), otherSession)
                .contentType("application/json")
                .content(vehicleJson(vehicleNumber, "Bike", "Honda", "Activa")))
            .andExpect(status().isNotFound());
        assertTrue(vehicleRepository.findById(vehicle.getId()).orElseThrow().isActive());
    }

    @Test
    void invalidVehicleAndDuplicateRegistrationAreRejected() throws Exception {
        MockHttpSession session = login(ownerEmail);
        mockMvc.perform(withCsrf(post("/api/owner/vehicles"), session)
                .contentType("application/json")
                .content(vehicleJson("INVALID", "Car", "Hyundai", "Creta")))
            .andExpect(status().isBadRequest());

        String vehicleNumber = uniqueValidVehicleNumber();
        mockMvc.perform(withCsrf(post("/api/owner/vehicles"), session)
                .contentType("application/json")
                .content(vehicleJson(vehicleNumber, "Car", "Tata", "Nexon")))
            .andExpect(status().isCreated());
        mockMvc.perform(withCsrf(post("/api/owner/vehicles"), session)
                .contentType("application/json")
                .content(vehicleJson(vehicleNumber, "Car", "Tata", "Nexon")))
            .andExpect(status().isConflict());
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
        request.session(session)
            .cookie(result.getResponse().getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN", token);
        return request;
    }

    private User account(String email) {
        User user = new User();
        user.setFullName("Vehicle Test Owner");
        user.setEmail(email);
        user.setPhone("9876543210");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(User.Role.OWNER);
        user.setActive(true);
        return user;
    }

    private String vehicleJson(String number, String type, String brand, String model) {
        return "{\"vehicleNumber\":\"" + number + "\",\"vehicleType\":\"" + type
            + "\",\"brand\":\"" + brand + "\",\"model\":\"" + model + "\"}";
    }

    private String uniqueValidVehicleNumber() {
        String candidate;
        do {
            String entropy = UUID.randomUUID().toString().replace("-", "");
            StringBuilder series = new StringBuilder(3);
            for (int index = 0; index < 3; index++) {
                int nibble = Character.digit(entropy.charAt(index), 16);
                series.append((char) ('A' + nibble));
            }
            int digits = Integer.parseInt(entropy.substring(3, 7), 16) % 10_000;
            candidate = "TS09" + series + String.format(java.util.Locale.ROOT, "%04d", digits);
        } while (vehicleRepository.existsByVehicleNumberIgnoreCase(candidate));
        return candidate;
    }
}
