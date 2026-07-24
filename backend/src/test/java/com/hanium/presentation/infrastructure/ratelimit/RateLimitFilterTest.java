package com.hanium.presentation.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.global.properties.AppSecurityProperties;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {
    private final AppSecurityProperties properties = new AppSecurityProperties();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(
            Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC));
    private final RateLimitFilter filter = new RateLimitFilter(limiter, properties, new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns429AndRetryAfterWhenLoginLimitIsExceeded() throws Exception {
        properties.getRateLimit().setLoginPerMinute(1);

        MockHttpServletResponse first = invoke("POST", "/api/auth/login", "127.0.0.1");
        MockHttpServletResponse second = invoke("POST", "/api/auth/login", "127.0.0.1");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotBlank();
        assertThat(second.getContentAsString()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
        assertThat(second.getContentAsString()).doesNotContain("127.0.0.1");
    }

    @Test
    void separatesAuthenticatedUsersForGeneralApiLimit() throws Exception {
        properties.getRateLimit().setApiPerMinute(1);

        authenticate("first@example.com");
        assertThat(invoke("GET", "/api/results", "127.0.0.1").getStatus()).isEqualTo(200);

        authenticate("second@example.com");
        assertThat(invoke("GET", "/api/results", "127.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresForwardedAddressUnlessExplicitlyTrusted() throws Exception {
        properties.getRateLimit().setLoginPerMinute(1);
        MockHttpServletRequest first = request("POST", "/api/auth/login", "10.0.0.1");
        first.addHeader("X-Forwarded-For", "192.0.2.1");
        MockHttpServletRequest second = request("POST", "/api/auth/login", "10.0.0.1");
        second.addHeader("X-Forwarded-For", "192.0.2.2");

        assertThat(invoke(first).getStatus()).isEqualTo(200);
        assertThat(invoke(second).getStatus()).isEqualTo(429);
    }

    @Test
    void usesForwardedAddressWhenProxyTrustIsEnabled() throws Exception {
        properties.setTrustProxyHeaders(true);
        properties.getRateLimit().setLoginPerMinute(1);
        MockHttpServletRequest first = request("POST", "/api/auth/login", "10.0.0.1");
        first.addHeader("X-Forwarded-For", "192.0.2.1");
        MockHttpServletRequest second = request("POST", "/api/auth/login", "10.0.0.1");
        second.addHeader("X-Forwarded-For", "192.0.2.2");

        assertThat(invoke(first).getStatus()).isEqualTo(200);
        assertThat(invoke(second).getStatus()).isEqualTo(200);
    }

    @Test
    void preflightRequestIsNotRateLimited() throws Exception {
        properties.getRateLimit().setApiPerMinute(1);

        assertThat(invoke("OPTIONS", "/api/results", "127.0.0.1").getStatus()).isEqualTo(200);
        assertThat(invoke("OPTIONS", "/api/results", "127.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void uploadAndRetryUseSeparateLimits() throws Exception {
        properties.getRateLimit().setUploadPerHour(1);
        properties.getRateLimit().setRetryPerHour(1);
        authenticate("owner@example.com");

        assertThat(invoke("POST", "/api/videos", "127.0.0.1").getStatus()).isEqualTo(200);
        assertThat(invoke("POST", "/api/videos", "127.0.0.1").getStatus()).isEqualTo(429);
        assertThat(invoke("POST", "/api/analyses/10/retry", "127.0.0.1").getStatus()).isEqualTo(200);
        assertThat(invoke("POST", "/api/analyses/10/retry", "127.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    void windowAllowsRequestsAgainAfterExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-24T00:00:00Z"));
        InMemoryRateLimiter expiringLimiter = new InMemoryRateLimiter(clock);

        assertThat(expiringLimiter.acquire("key", 1, java.time.Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(expiringLimiter.acquire("key", 1, java.time.Duration.ofMinutes(1)).allowed()).isFalse();

        clock.advance(java.time.Duration.ofMinutes(1));

        assertThat(expiringLimiter.acquire("key", 1, java.time.Duration.ofMinutes(1)).allowed()).isTrue();
    }

    private void authenticate(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    private MockHttpServletResponse invoke(String method, String path, String remoteAddress) throws Exception {
        return invoke(request(method, path, remoteAddress));
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        if (response.getStatus() != 429) {
            verify(chain).doFilter(request, response);
        }
        return response;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(java.time.Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
