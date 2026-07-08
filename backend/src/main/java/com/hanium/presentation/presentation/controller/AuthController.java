// 역할: HTTP 요청을 받아 애플리케이션 서비스로 전달하고 API 응답을 구성하는 컨트롤러입니다.
package com.hanium.presentation.presentation.controller;

import com.hanium.presentation.application.auth.AuthService;
import com.hanium.presentation.application.auth.CurrentUserService;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.global.response.ApiResponse;
import com.hanium.presentation.presentation.dto.request.LoginRequest;
import com.hanium.presentation.presentation.dto.request.SignupRequest;
import com.hanium.presentation.presentation.dto.response.AuthTokenResponse;
import com.hanium.presentation.presentation.dto.response.SignupResponse;
import com.hanium.presentation.presentation.dto.response.UserMeResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    // 함수 역할: AuthController에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    // 함수 역할: signup에서 회원가입 입력값을 검증하고 사용자 계정을 생성합니다.
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request), "회원가입이 완료되었습니다.");
    }

    // 함수 역할: login에서 로그인 요청을 검증하고 인증 토큰 발급 흐름을 처리합니다.
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        response.addCookie(refreshCookie(result.refreshToken(), 14 * 24 * 60 * 60));
        return ApiResponse.ok(result.response(), "로그인되었습니다.");
    }

    // 함수 역할: refresh에서 인증 토큰 생성, 검증, 재발급에 필요한 처리를 수행합니다.
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(HttpServletRequest request) {
        return ApiResponse.ok(authService.refresh(readRefreshToken(request)), "토큰이 재발급되었습니다.");
    }

    // 함수 역할: logout에서 로그아웃 요청에 맞춰 세션 또는 토큰 정보를 정리합니다.
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(readRefreshTokenOrNull(request));
        response.addCookie(refreshCookie("", 0));
        return ApiResponse.ok(null, "로그아웃되었습니다.");
    }

    // 함수 역할: me에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me() {
        return ApiResponse.ok(authService.me(currentUserService.currentUser()), "인증 상태입니다.");
    }

    // 함수 역할: refreshCookie에서 인증 토큰 생성, 검증, 재발급에 필요한 처리를 수행합니다.
    private Cookie refreshCookie(String value, int maxAge) {
        Cookie cookie = new Cookie("refreshToken", value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    // 함수 역할: readRefreshToken에서 저장소나 외부 시스템의 데이터를 조회합니다.
    private String readRefreshToken(HttpServletRequest request) {
        String token = readRefreshTokenOrNull(request);
        if (token == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return token;
    }

    // 함수 역할: readRefreshTokenOrNull에서 저장소나 외부 시스템의 데이터를 조회합니다.
    private String readRefreshTokenOrNull(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
