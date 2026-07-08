// 역할: 공통 예외 코드와 예외 응답 처리를 담당합니다.
package com.hanium.presentation.global.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    // 함수 역할: BusinessException에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    // 함수 역할: errorCode에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public ErrorCode errorCode() {
        return errorCode;
    }
}
