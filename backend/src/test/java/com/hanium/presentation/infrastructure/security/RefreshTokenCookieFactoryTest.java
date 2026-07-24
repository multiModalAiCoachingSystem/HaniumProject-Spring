package com.hanium.presentation.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanium.presentation.global.properties.AppSecurityProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RefreshTokenCookieFactoryTest {
    @Test
    void developmentCookieIsHttpOnlyLaxAndNotSecure() {
        AppSecurityProperties properties = new AppSecurityProperties();
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(properties);

        String cookie = factory.create("token", Duration.ofDays(14)).toString();

        assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(cookie).doesNotContain("Secure");
    }

    @Test
    void productionCookieUsesSecureAttribute() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.getCookie().setSecure(true);
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(properties);

        String cookie = factory.create("token", Duration.ofDays(14)).toString();

        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
    }
}
