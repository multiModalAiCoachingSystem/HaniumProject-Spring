// 역할: API 응답 형식을 일관되게 감싸는 공통 응답 모델입니다.
package com.hanium.presentation.global.response;

public record ApiResponse<T>(boolean success, T data, String message, String errorCode) {
    // 함수 역할: ok에서 외부 API 또는 백엔드 API 호출을 수행합니다.
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    // 함수 역할: fail에서 외부 API 또는 백엔드 API 호출을 수행합니다.
    public static <T> ApiResponse<T> fail(String message, String errorCode) {
        return new ApiResponse<>(false, null, message, errorCode);
    }
}
