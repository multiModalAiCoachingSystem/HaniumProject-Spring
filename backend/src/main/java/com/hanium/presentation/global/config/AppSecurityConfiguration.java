package com.hanium.presentation.global.config;

import com.hanium.presentation.global.properties.AppSecurityProperties;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppSecurityProperties.class)
public class AppSecurityConfiguration {
    private static final Set<String> SAME_SITE_VALUES = Set.of("lax", "strict", "none");
    private final AppSecurityProperties properties;

    public AppSecurityConfiguration(AppSecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.getFrontendOrigins() == null || properties.getFrontendOrigins().isEmpty()) {
            throw new IllegalStateException("FRONTEND_ORIGINS must contain at least one explicit origin");
        }
        if (properties.getFrontendOrigins().stream().anyMatch(origin -> origin == null
                || origin.isBlank() || origin.contains("*"))) {
            throw new IllegalStateException("FRONTEND_ORIGINS must not be blank or contain wildcards");
        }
        properties.getFrontendOrigins().forEach(this::validateOrigin);

        String sameSite = properties.getCookie().getSameSite();
        if (sameSite == null || !SAME_SITE_VALUES.contains(sameSite.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("COOKIE_SAME_SITE must be Lax, Strict, or None");
        }
        if ("none".equalsIgnoreCase(sameSite) && !properties.getCookie().isSecure()) {
            throw new IllegalStateException("COOKIE_SECURE must be true when COOKIE_SAME_SITE=None");
        }

        URI baseUri;
        try {
            baseUri = URI.create(properties.getBaseUrl());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_BASE_URL must be a valid absolute URL", exception);
        }
        if (!baseUri.isAbsolute()) {
            throw new IllegalStateException("APP_BASE_URL must be an absolute URL");
        }
        if (baseUri.getHost() == null) {
            throw new IllegalStateException("APP_BASE_URL must include a host");
        }
        if (properties.isProduction()
                && (!"https".equalsIgnoreCase(baseUri.getScheme()) || !properties.getCookie().isSecure())) {
            throw new IllegalStateException("Production requires an HTTPS APP_BASE_URL and COOKIE_SECURE=true");
        }
        validatePositiveRateLimits();
    }

    private void validateOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!supportedScheme || uri.getHost() == null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("FRONTEND_ORIGINS entries must be exact HTTP(S) origins");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("FRONTEND_ORIGINS contains an invalid origin", exception);
        }
    }

    private void validatePositiveRateLimits() {
        AppSecurityProperties.RateLimit limits = properties.getRateLimit();
        if (limits.getLoginPerMinute() < 1 || limits.getRegisterPerHour() < 1
                || limits.getRefreshPerMinute() < 1 || limits.getUploadPerHour() < 1
                || limits.getAnalysisPerHour() < 1 || limits.getRetryPerHour() < 1
                || limits.getAdminPerMinute() < 1 || limits.getApiPerMinute() < 1) {
            throw new IllegalStateException("All RATE_LIMIT values must be positive");
        }
    }
}
