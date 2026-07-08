// 역할: JWT 인증, 권한 검증, 보안 필터 체인을 구성하는 보안 컴포넌트입니다.
package com.hanium.presentation.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.domain.user.type.UserRole;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String secret;
    private byte[] secretBytes;

    // 함수 역할: 필요한 의존성과 초기값을 받아 객체를 생성합니다.
    public JwtTokenProvider(@Value("${JWT_SECRET}") String secret) {
        this.secret = secret;
    }

    // 함수 역할: init에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable is required.");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    // 함수 역할: createAccessToken에서 새 데이터나 처리 결과를 생성해 저장합니다.
    public String createAccessToken(String email, UserRole role) {
        long now = Instant.now().getEpochSecond();
        return sign(Map.of("alg", "HS256", "typ", "JWT"), Map.of(
                "sub", email,
                "role", role.name(),
                "iat", now,
                "exp", now + 60 * 30
        ));
    }

    // 함수 역할: getSubject에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getSubject(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !signature(parts[0] + "." + parts[1]).equals(parts[2])) {
                return null;
            }
            Map<?, ?> payload = OBJECT_MAPPER.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Number exp = (Number) payload.get("exp");
            if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
                return null;
            }
            return (String) payload.get("sub");
        } catch (Exception exception) {
            return null;
        }
    }

    // 함수 역할: sign에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private String sign(Map<String, Object> header, Map<String, Object> payload) {
        try {
            String headerPart = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
            String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
            String body = headerPart + "." + payloadPart;
            return body + "." + signature(body);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 생성 실패", exception);
        }
    }

    // 함수 역할: signature에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
