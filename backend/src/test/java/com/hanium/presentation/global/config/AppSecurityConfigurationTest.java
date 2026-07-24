package com.hanium.presentation.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanium.presentation.global.properties.AppSecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppSecurityConfigurationTest {
    @Test
    void rejectsWildcardCorsOrigin() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setFrontendOrigins(List.of("*"));

        assertThatThrownBy(() -> new AppSecurityConfiguration(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcards");
    }

    @Test
    void rejectsInsecureProductionConfiguration() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setEnvironment("production");
        properties.setBaseUrl("http://example.invalid");

        assertThatThrownBy(() -> new AppSecurityConfiguration(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production requires");
    }

    @Test
    void acceptsHttpsProductionConfigurationWithSecureCookie() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setEnvironment("production");
        properties.setBaseUrl("https://example.invalid");
        properties.getCookie().setSecure(true);

        new AppSecurityConfiguration(properties).validate();
    }
}
