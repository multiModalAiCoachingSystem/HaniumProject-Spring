// 역할: 공통 예외 코드와 예외 응답 처리를 담당합니다.
package com.hanium.presentation.global.exception;

public record ErrorResponse(String errorCode, String message) {
}
