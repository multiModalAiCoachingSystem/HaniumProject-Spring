// 역할: 공통 예외 코드와 예외 응답 처리를 담당합니다.
package com.hanium.presentation.global.exception;

import com.hanium.presentation.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AnalysisAlreadyActiveException.class)
    public ResponseEntity<ApiResponse<Map<String, Long>>> handleAnalysisAlreadyActive(
            AnalysisAlreadyActiveException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, Map.of("jobId", exception.jobId()), exception.getMessage(),
                        exception.errorCode().name()));
    }

    // 함수 역할: handleBusinessException에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(statusOf(exception.errorCode()))
                .body(ApiResponse.fail(exception.getMessage(), exception.errorCode().name()));
    }

    // 함수 역할: handleValidationException에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, "VALIDATION_ERROR"));
    }

    // 함수 역할: handleAccessDeniedException에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException() {
        return ResponseEntity.status(403).body(ApiResponse.fail(ErrorCode.ACCESS_DENIED.message(), ErrorCode.ACCESS_DENIED.name()));
    }

    private HttpStatus statusOf(ErrorCode errorCode) {
        return switch (errorCode) {
            case ACCESS_DENIED, VIDEO_OWNER_MISMATCH -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND, VIDEO_NOT_FOUND, ANALYSIS_JOB_NOT_FOUND, RESULT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ANALYSIS_ALREADY_ACTIVE, ANALYSIS_ALREADY_RUNNING, ANALYSIS_RETRY_NOT_ALLOWED, ANALYSIS_CANCEL_NOT_ALLOWED, ANALYSIS_SOURCE_VIDEO_MISSING,
                    ANALYSIS_DELETE_NOT_ALLOWED -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
