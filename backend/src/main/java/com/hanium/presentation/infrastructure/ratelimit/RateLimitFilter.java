package com.hanium.presentation.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.global.properties.AppSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final InMemoryRateLimiter rateLimiter;
    private final AppSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(InMemoryRateLimiter rateLimiter, AppSecurityProperties properties, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/health".equals(request.getRequestURI())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Policy policy = resolvePolicy(request);
        String identity = authenticatedIdentity();
        if (identity == null || policy.category() == RateLimitCategory.LOGIN
                || policy.category() == RateLimitCategory.REGISTER
                || policy.category() == RateLimitCategory.REFRESH) {
            identity = clientAddress(request);
        }

        String bucketKey = policy.category() + ":" + identity;
        InMemoryRateLimiter.Decision decision = rateLimiter.acquire(bucketKey, policy.limit(), policy.window());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded: category={} clientKeyHash={} retryAfterSeconds={}",
                policy.category(), shortHash(bucketKey), decision.retryAfterSeconds());
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                new RateLimitResponse(ErrorCode.RATE_LIMIT_EXCEEDED.name(),
                        ErrorCode.RATE_LIMIT_EXCEEDED.message(), decision.retryAfterSeconds()));
    }

    private Policy resolvePolicy(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        AppSecurityProperties.RateLimit limits = properties.getRateLimit();

        if ("POST".equals(method) && "/api/auth/login".equals(path)) {
            return new Policy(RateLimitCategory.LOGIN, limits.getLoginPerMinute(), Duration.ofMinutes(1));
        }
        if ("POST".equals(method) && "/api/auth/signup".equals(path)) {
            return new Policy(RateLimitCategory.REGISTER, limits.getRegisterPerHour(), Duration.ofHours(1));
        }
        if ("POST".equals(method) && "/api/auth/refresh".equals(path)) {
            return new Policy(RateLimitCategory.REFRESH, limits.getRefreshPerMinute(), Duration.ofMinutes(1));
        }
        if ("POST".equals(method) && "/api/videos".equals(path)) {
            return new Policy(RateLimitCategory.UPLOAD, limits.getUploadPerHour(), Duration.ofHours(1));
        }
        if ("POST".equals(method) && "/api/analyses".equals(path)) {
            return new Policy(RateLimitCategory.ANALYSIS, limits.getAnalysisPerHour(), Duration.ofHours(1));
        }
        if ("POST".equals(method) && path.matches("/api/analyses/\\d+/retry")) {
            return new Policy(RateLimitCategory.RETRY, limits.getRetryPerHour(), Duration.ofHours(1));
        }
        if (path.startsWith("/api/admin/")) {
            return new Policy(RateLimitCategory.ADMIN, limits.getAdminPerMinute(), Duration.ofMinutes(1));
        }
        return new Policy(RateLimitCategory.API, limits.getApiPerMinute(), Duration.ofMinutes(1));
    }

    private String authenticatedIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return "user:" + authentication.getName();
    }

    private String clientAddress(HttpServletRequest request) {
        if (properties.isTrustProxyHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null) {
                String candidate = forwardedFor.split(",", 2)[0].trim();
                if (candidate.matches("[0-9a-fA-F:.]{3,64}")) {
                    return "ip:" + candidate;
                }
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception exception) {
            return "unavailable";
        }
    }

    record Policy(RateLimitCategory category, int limit, Duration window) {
    }

    record RateLimitResponse(String code, String message, long retryAfterSeconds) {
    }
}
