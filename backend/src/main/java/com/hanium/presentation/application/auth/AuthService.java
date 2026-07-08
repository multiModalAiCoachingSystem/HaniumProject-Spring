// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.auth;

import com.hanium.presentation.domain.auth.entity.RefreshToken;
import com.hanium.presentation.domain.auth.repository.RefreshTokenRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.repository.UserRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.infrastructure.security.JwtTokenProvider;
import com.hanium.presentation.presentation.dto.request.LoginRequest;
import com.hanium.presentation.presentation.dto.request.SignupRequest;
import com.hanium.presentation.presentation.dto.response.AuthTokenResponse;
import com.hanium.presentation.presentation.dto.response.SignupResponse;
import com.hanium.presentation.presentation.dto.response.UserMeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 함수 역할: signup에서 회원가입 입력값을 검증하고 사용자 계정을 생성합니다.
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.createUser(email, passwordEncoder.encode(request.password()), request.nickname().trim());
        return SignupResponse.from(userRepository.save(user));
    }

    // 함수 역할: login에서 로그인 요청을 검증하고 인증 토큰 발급 흐름을 처리합니다.
    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(User::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        String refreshToken = UUID.randomUUID().toString() + UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(user, hash(refreshToken), LocalDateTime.now().plusDays(14)));
        return new LoginResult(new AuthTokenResponse(
                jwtTokenProvider.createAccessToken(user.getEmail(), user.getRole()),
                user.getRole().name(),
                user.getEmail(),
                user.getNickname()
        ), refreshToken);
    }

    // 함수 역할: refresh에서 인증 토큰 생성, 검증, 재발급에 필요한 처리를 수행합니다.
    @Transactional
    public AuthTokenResponse refresh(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash(refreshToken))
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        User user = token.getUser();
        return new AuthTokenResponse(jwtTokenProvider.createAccessToken(user.getEmail(), user.getRole()),
                user.getRole().name(), user.getEmail(), user.getNickname());
    }

    // 함수 역할: logout에서 로그아웃 요청에 맞춰 세션 또는 토큰 정보를 정리합니다.
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash(refreshToken)).ifPresent(RefreshToken::revoke);
    }

    // 함수 역할: me에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public UserMeResponse me(User user) {
        return UserMeResponse.from(user);
    }

    // 함수 역할: hash에서 필요한 값이나 상태를 조회해 반환합니다.
    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("토큰 해시 생성 실패", exception);
        }
    }

    public record LoginResult(AuthTokenResponse response, String refreshToken) {
    }
}
