// 역할: HTTP 요청을 받아 애플리케이션 서비스로 전달하고 API 응답을 구성하는 컨트롤러입니다.
package com.hanium.presentation.presentation.controller;

import com.hanium.presentation.application.analysis.AnalysisCommandService;
import com.hanium.presentation.application.auth.CurrentUserService;
import com.hanium.presentation.common.util.AnalysisFailureMessages;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.auth.repository.RefreshTokenRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.repository.UserRepository;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.global.response.ApiResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserService currentUserService;
    private final AnalysisCommandService analysisCommandService;

    // 함수 역할: AdminController에서 운영 콘솔에 필요한 저장소와 현재 사용자 조회 서비스를 주입받습니다.
    public AdminController(UserRepository userRepository,
                           AnalysisJobRepository analysisJobRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           CurrentUserService currentUserService,
                           AnalysisCommandService analysisCommandService) {
        this.userRepository = userRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.currentUserService = currentUserService;
        this.analysisCommandService = analysisCommandService;
    }

    // 함수 역할: 권한이 높은 순서대로 정렬된 사용자 목록을 운영 콘솔에 반환합니다.
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users() {
        return ApiResponse.ok(userRepository.findAll().stream()
                .filter(User::isEnabled)
                .sorted(Comparator
                        .comparingInt((User user) -> user.getRole().priority()).reversed()
                        .thenComparing(User::getId))
                .map(user -> Map.<String, Object>of(
                        "userId", user.getId(),
                        "email", user.getEmail(),
                        "nickname", user.getNickname(),
                        "role", user.getRole().name(),
                        "enabled", user.isEnabled()
                ))
                .toList(), "사용자 목록입니다.");
    }

    // 함수 역할: ADMIN 권한으로 USER와 MANAGER 사이의 사용자 권한을 변경합니다.
    @PatchMapping("/users/{userId}/role")
    @Transactional
    public ApiResponse<Map<String, Object>> updateUserRole(@PathVariable Long userId, @RequestBody Map<String, String> request) {
        User currentUser = currentUserService.currentUser();
        if (!currentUser.getRole().canExpelUsers()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetUser.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_ACTION_NOT_ALLOWED);
        }

        UserRole nextRole = parseManageableRole(request.get("role"));
        targetUser.changeRole(nextRole);

        return ApiResponse.ok(Map.<String, Object>of(
                "userId", targetUser.getId(),
                "role", targetUser.getRole().name()
        ), "사용자 권한을 변경했습니다.");
    }

    // 함수 역할: 운영 콘솔에서 변경 가능한 USER 또는 MANAGER 권한값인지 검증합니다.
    private UserRole parseManageableRole(String roleValue) {
        try {
            UserRole role = UserRole.valueOf(roleValue);
            if (role == UserRole.USER || role == UserRole.MANAGER) {
                return role;
            }
            throw new BusinessException(ErrorCode.INVALID_ROLE_CHANGE);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_CHANGE);
        }
    }

    // 함수 역할: ADMIN 권한으로 일반 사용자를 추방 처리하고 기존 refresh token을 폐기합니다.
    @DeleteMapping("/users/{userId}")
    @Transactional
    public ApiResponse<Map<String, Object>> expelUser(@PathVariable Long userId) {
        User currentUser = currentUserService.currentUser();
        if (!currentUser.getRole().canExpelUsers()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (targetUser.getRole().isProtectedOperationAccount()) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_ACTION_NOT_ALLOWED);
        }

        targetUser.disable();
        refreshTokenRepository.findByUserIdAndRevokedFalse(targetUser.getId())
                .forEach(token -> token.revoke());

        return ApiResponse.ok(Map.<String, Object>of(
                "userId", targetUser.getId(),
                "enabled", targetUser.isEnabled()
        ), "사용자를 추방했습니다.");
    }

    // 함수 역할: 운영 콘솔에 표시할 분석 작업 목록을 반환합니다.
    @GetMapping("/analyses")
    public ApiResponse<List<Map<String, Object>>> analyses(@RequestParam(required = false) AnalysisStatus status) {
        var jobs = status == null ? analysisJobRepository.findAll() : analysisJobRepository.findByStatusOrderByCreatedAtDesc(status);
        return ApiResponse.ok(jobs.stream()
                .map(this::analysisRow)
                .toList(), "분석 작업 목록입니다.");
    }

    // 함수 역할: ADMIN 또는 MANAGER 권한으로 선택한 분석 작업과 연결된 최종 결과를 함께 삭제합니다.
    @DeleteMapping("/analyses/{jobId}")
    public ApiResponse<Map<String, Object>> deleteAnalysis(@PathVariable Long jobId) {
        analysisCommandService.deleteReport(currentUserService.currentUser(), jobId);


        return ApiResponse.ok(Map.<String, Object>of("jobId", jobId), "분석 작업을 삭제했습니다.");
    }

    // 함수 역할: 서비스 상태 확인 요청에 대한 응답을 반환합니다.
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("backend", "UP", "analysisEngine", "MOCK", "videoLlmEngine", "MOCK"), "시스템 상태입니다.");
    }

    private Long elapsedSeconds(AnalysisJob job) {
        LocalDateTime startedAt = job.getStartedAt();
        if (startedAt == null) {
            return null;
        }

        LocalDateTime endedAt = job.getCompletedAt() == null ? LocalDateTime.now() : job.getCompletedAt();
        return Math.max(0, Duration.between(startedAt, endedAt).getSeconds());
    }

    private Map<String, Object> analysisRow(AnalysisJob job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", job.getId());
        row.put("status", job.getStatus().name());
        row.put("step", job.getStep().name());
        row.put("progressPercent", job.getProgressPercent());
        row.put("retryCount", job.getRetryCount());
        row.put("message", job.getMessage());
        row.put("errorCode", job.getStatus() == AnalysisStatus.FAILED
                ? AnalysisFailureMessages.codeFromStoredMessage(job.getErrorMessage())
                : null);
        row.put("errorMessage", job.getStatus() == AnalysisStatus.FAILED
                ? AnalysisFailureMessages.safeStoredMessage(job.getErrorMessage())
                : null);
        row.put("failedAt", job.getStatus() == AnalysisStatus.FAILED ? job.getCompletedAt() : null);
        row.put("elapsedSeconds", elapsedSeconds(job));
        return row;
    }
}
