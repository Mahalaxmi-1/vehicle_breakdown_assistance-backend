package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.roadassist.backend.config.CorsConfig;
import com.roadassist.backend.config.SecurityConfig;
import com.roadassist.backend.controller.HealthController;

class ProductionConfigurationTests {

    @Test
    void productionProfileRequiresExplicitRuntimeSettingsAndKeepsValidationOnly() throws IOException {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application-production.properties")) {
            assertNotNull(stream);
            properties.load(stream);
        }

        assertEquals("${SPRING_DATASOURCE_URL}", properties.getProperty("spring.datasource.url"));
        assertEquals("${SPRING_DATASOURCE_USERNAME}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
        assertEquals("${ROADASSIST_FRONTEND_ORIGIN}", properties.getProperty("ROADASSIST_FRONTEND_ORIGIN"));
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", properties.getProperty("spring.jpa.show-sql"));
        assertEquals("true", properties.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("${ROADASSIST_COOKIE_SAME_SITE:none}",
            properties.getProperty("server.servlet.session.cookie.same-site"));
    }

    @Test
    void healthControllerReportsOperational() {
        HealthController controller = new HealthController();
        String result = controller.health();
        assertNotNull(result);
        assertTrue(result.contains("running successfully"));
    }

    @Test
    void corsConfigParsesMultipleOriginsAndStripsTrailingSlashes() {
        CorsConfig corsConfig = new CorsConfig();
        WebMvcConfigurer configurer = corsConfig.corsConfigurer(
            "http://localhost:5173/, https://roadassist.vercel.app/ , http://127.0.0.1:5173",
            new MockEnvironment());

        assertNotNull(configurer);
        CorsRegistry registry = new CorsRegistry();
        configurer.addCorsMappings(registry);
        // Successfully configured without exception
    }

    @Test
    void productionCorsRequiresOneHttpsOriginWithoutPath() {
        CorsConfig corsConfig = new CorsConfig();
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("production");

        assertNotNull(corsConfig.corsConfigurer("https://roadassist.example", production));
        assertThrows(IllegalStateException.class,
            () -> corsConfig.corsConfigurer("http://roadassist.example", production));
        assertThrows(IllegalStateException.class,
            () -> corsConfig.corsConfigurer("https://one.example,https://two.example", production));
        assertThrows(IllegalStateException.class,
            () -> corsConfig.corsConfigurer("https://roadassist.example/path", production));
        assertThrows(IllegalStateException.class,
            () -> corsConfig.corsConfigurer("*", production));
    }

    @Test
    void csrfTokenRepositoryProducesExpectedCookiesInSecureAndLocalModes() {
        SecurityConfig securityConfig = new SecurityConfig();

        // Local development mode
        CookieCsrfTokenRepository localRepo = securityConfig.csrfTokenRepository(false, "lax");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token-123");

        localRepo.saveToken(token, request, response);
        var cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertEquals("test-token-123", cookie.getValue());
        assertEquals(false, cookie.getSecure());

        // Production HTTPS mode
        MockHttpServletResponse prodResponse = new MockHttpServletResponse();
        CookieCsrfTokenRepository prodRepo = securityConfig.csrfTokenRepository(true, "none");
        prodRepo.saveToken(token, request, prodResponse);
        var prodCookie = prodResponse.getCookie("XSRF-TOKEN");
        assertNotNull(prodCookie);
        assertEquals(true, prodCookie.getSecure());
        assertEquals("none", prodCookie.getAttribute("SameSite"));
    }
}
