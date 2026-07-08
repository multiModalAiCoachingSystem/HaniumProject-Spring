package com.hanium.presentation.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanium.presentation.domain.auth.entity.RefreshToken;
import com.hanium.presentation.domain.auth.repository.RefreshTokenRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.repository.UserRepository;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.infrastructure.security.JwtTokenProvider;
import com.hanium.presentation.presentation.dto.request.LoginRequest;
import com.hanium.presentation.presentation.dto.request.SignupRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
    private final AuthService authService = new AuthService(userRepository, refreshTokenRepository,
            passwordEncoder, jwtTokenProvider);

    @Test
    void signupCreatesUserWithNormalizedEmailAndEncodedPassword() {
        SignupRequest request = new SignupRequest(" OWNER@EXAMPLE.COM ", "password123", "password123", " owner ");
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        var response = authService.signup(request);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("owner@example.com");
        assertThat(response.nickname()).isEqualTo("owner");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void loginIssuesAccessAndRefreshTokensForValidCredentials() {
        User user = user(1L, "owner@example.com", UserRole.USER);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPassword())).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(user.getEmail(), user.getRole())).thenReturn("access-token");

        var result = authService.login(new LoginRequest(" OWNER@EXAMPLE.COM ", "password123"));

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().email()).isEqualTo("owner@example.com");
        assertThat(result.response().role()).isEqualTo("USER");
        assertThat(result.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsWrongPasswordWithoutIssuingTokens() {
        User user = user(1L, "owner@example.com", UserRole.USER);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-password", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("owner@example.com", "bad-password")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS));

        verify(jwtTokenProvider, never()).createAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    private User user(Long id, String email, UserRole role) {
        User user = User.createUser(email, "encoded-password", "owner");
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        return user;
    }
}
