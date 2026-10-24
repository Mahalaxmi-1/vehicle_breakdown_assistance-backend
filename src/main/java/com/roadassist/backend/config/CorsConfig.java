
package com.roadassist.backend.config;

import java.net.URI;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${ROADASSIST_FRONTEND_ORIGIN:http://localhost:5173}") String frontendOrigin,
            Environment environment) {

        String[] allowedOrigins = Arrays.stream(frontendOrigin.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(origin -> origin.replaceAll("/+$", ""))
                .toArray(String[]::new);

        if (allowedOrigins.length == 0
                || Arrays.stream(allowedOrigins).anyMatch(
                        origin -> origin.equals("*")
                                || origin.equalsIgnoreCase("null"))) {
            throw new IllegalStateException(
                "CORS requires explicit frontend origins; wildcard and null origins are not allowed.");
        }

        if (environment.acceptsProfiles(Profiles.of("production"))) {
            if (Arrays.stream(allowedOrigins).anyMatch(origin -> !isHttpsOrigin(origin))) {
                throw new IllegalStateException(
                        "Production CORS requires HTTPS frontend origins without paths.");
            }
        }

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "Accept", "X-XSRF-TOKEN")
                        .allowCredentials(true);
            }
        };
    }

    private boolean isHttpsOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
