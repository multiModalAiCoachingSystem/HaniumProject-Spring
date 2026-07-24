package com.hanium.presentation.infrastructure.security;

import com.hanium.presentation.global.properties.AppSecurityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {
    private static final String COOKIE_NAME = "refreshToken";
    private final AppSecurityProperties properties;

    public RefreshTokenCookieFactory(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
