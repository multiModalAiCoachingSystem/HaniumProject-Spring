package com.hanium.presentation.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.global.properties.AppSecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {
    @Bean
    RateLimitFilter rateLimitFilter(InMemoryRateLimiter rateLimiter,
                                    AppSecurityProperties properties,
                                    ObjectMapper objectMapper) {
        return new RateLimitFilter(rateLimiter, properties, objectMapper);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> disableRateLimitServletRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
