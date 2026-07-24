package com.hanium.presentation.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanium.presentation.global.properties.AppSecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {
    @Test
    void allowsOnlyConfiguredOriginsWithCredentials() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setFrontendOrigins(List.of("http://localhost:5173"));
        CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/results");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
    }
}
