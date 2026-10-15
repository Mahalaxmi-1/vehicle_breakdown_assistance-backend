package com.roadassist.backend;

import java.util.regex.Pattern;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Refuses to start database-backed tests unless they target the local test schema. */
@Configuration(proxyBeanMethods = false)
class TestDatabaseSafetyConfiguration {
    private static final Pattern SAFE_TEST_URL = Pattern.compile(
        "^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?/roadassist_test(?:\\?.*)?$",
        Pattern.CASE_INSENSITIVE);

    @Bean
    static BeanFactoryPostProcessor roadAssistTestDatabaseUrlGuard(Environment environment) {
        validateUrl(environment.getProperty("spring.datasource.url"));
        return beanFactory -> { };
    }

    static void validateUrl(String url) {
        if (url == null || !SAFE_TEST_URL.matcher(url).matches()) {
            throw new BeanDefinitionStoreException(
                "Database-backed tests require local MySQL schema roadassist_test; refusing to start.");
        }
    }
}
