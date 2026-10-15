package com.roadassist.backend;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.roadassist.backend.config.CorsConfig;
import com.roadassist.backend.config.SecurityConfig;
import com.roadassist.backend.controller.HealthController;
import com.roadassist.backend.controller.UserController;
import com.roadassist.backend.dto.RegisterRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.security.RoadAssistUserDetailsService;
import com.roadassist.backend.service.UserService;

@WebMvcTest(controllers = {UserController.class, HealthController.class})
@Import({SecurityConfig.class, CorsConfig.class, RoadAssistUserDetailsService.class})
class AuthSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUpUsers() {
        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
            .thenReturn(Optional.of(account(1L, "owner@example.com", User.Role.OWNER)));
        when(userRepository.findByEmailIgnoreCase("mechanic@example.com"))
            .thenReturn(Optional.of(account(2L, "mechanic@example.com", User.Role.MECHANIC)));
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
            .thenReturn(Optional.of(account(3L, "admin@example.com", User.Role.ADMIN)));
    }

    @Test
    void publicAdminRegistrationIsRejectedByValidation() throws Exception {
        mockMvc.perform(withCsrf(post("/api/auth/register"), null)
                .contentType("application/json")
                .content("""
                    {"fullName":"Admin","email":"admin-new@example.com","phone":"9876543210",
                     "password":"password123","role":"ADMIN"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ownerAndMechanicCanAuthenticateAndReceiveSafeUserData() throws Exception {
        for (String[] credentials : new String[][] {
                {"owner@example.com", "OWNER"},
                {"mechanic@example.com", "MECHANIC"}}) {
            MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/login"), null)
                    .contentType("application/json")
                    .content(loginJson(credentials[0], credentials[1])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.role").value(credentials[1]))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

            if ("owner@example.com".equals(credentials[0])) {
                mockMvc.perform(get("/api/auth/me")
                        .session((MockHttpSession) result.getRequest().getSession(false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.role").value("OWNER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
            }
        }
    }

    @Test
    void healthIsPublicAndProtectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void roleRulesRestrictAdminEndpoints() throws Exception {
        MockHttpSession ownerSession = login("owner@example.com", "OWNER");
        mockMvc.perform(get("/api/admin/probe").session(ownerSession))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/owner/probe").session(ownerSession))
            .andExpect(status().isNotFound());

        MockHttpSession mechanicSession = login("mechanic@example.com", "MECHANIC");
        mockMvc.perform(get("/api/owner/probe").session(mechanicSession))
            .andExpect(status().isForbidden());

        MockHttpSession adminSession = login("admin@example.com", "ADMIN");
        // There is no probe controller; reaching MVC's 404 proves authorization allowed ADMIN.
        mockMvc.perform(get("/api/admin/probe").session(adminSession))
            .andExpect(status().isNotFound());
    }

    @Test
    void unsafeRequestsRequireCsrfAndCsrfEndpointIssuesCookie() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(loginJson("owner@example.com", "OWNER")))
            .andExpect(status().isForbidden());

        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(
            csrfResult.getResponse().getContentAsString(), "$.token");
        jakarta.servlet.http.Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertEquals(token, csrfCookie.getValue());
        MockHttpSession csrfSession = (MockHttpSession) csrfResult.getRequest().getSession(false);

        when(userService.register(any(RegisterRequest.class)))
            .thenReturn(account(4L, "registered@example.com", User.Role.OWNER));
        var registration = post("/api/auth/register")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType("application/json")
                .content("""
                    {"fullName":"Registered Owner","email":"registered@example.com",
                     "phone":"9876543211","password":"password123","role":"OWNER"}
                    """);
        if (csrfSession != null) {
            registration.session(csrfSession);
        }
        MvcResult registrationResult = mockMvc.perform(registration).andReturn();
        verify(userService).register(any(RegisterRequest.class));
        assertEquals(201, registrationResult.getResponse().getStatus());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("owner@example.com", "OWNER");
        mockMvc.perform(withCsrf(post("/api/auth/logout"), session))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/login"), null)
                .contentType("application/json")
                .content(loginJson(email, role)))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var csrfRequest = get("/api/auth/csrf");
        if (session != null) {
            csrfRequest.session(session);
        }
        MvcResult result = mockMvc.perform(csrfRequest)
            .andExpect(status().isOk())
            .andReturn();
        jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        String token = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.token");
        request.cookie(cookie).header("X-XSRF-TOKEN", token);
        if (session != null) {
            request.session(session);
        }
        return request;
    }

    private String loginJson(String email, String role) {
        return "{\"email\":\"" + email + "\",\"password\":\"password123\",\"role\":\"" + role + "\"}";
    }

    private User account(Long id, String email, User.Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPhone("9876543210");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
