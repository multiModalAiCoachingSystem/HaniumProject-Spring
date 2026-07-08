package com.hanium.presentation.global.exception;

public enum ErrorCode {
    AUTHENTICATION_FAILED("인증에 실패했습니다."),
    BAD_CREDENTIALS("이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS("이미 가입된 이메일입니다."),
    PASSWORD_CONFIRM_MISMATCH("비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    REFRESH_TOKEN_INVALID("Refresh Token이 유효하지 않습니다."),
    ACCESS_DENIED("권한이 없습니다."),
    ADMIN_ACCOUNT_ACTION_NOT_ALLOWED("관리자 계정은 권한 변경 또는 추방 대상이 아닙니다."),
    INVALID_ROLE_CHANGE("변경할 수 없는 권한입니다."),
    USER_NOT_FOUND("사용자를 찾을 수 없습니다."),
    VIDEO_NOT_FOUND("영상을 찾을 수 없습니다."),
    VIDEO_OWNER_MISMATCH("영상 접근 권한이 없습니다."),
    INVALID_VIDEO_TYPE("지원하지 않는 영상 형식입니다."),
    VIDEO_FILE_TOO_LARGE("영상 파일 용량이 너무 큽니다."),
    STORAGE_ERROR("파일 저장 중 오류가 발생했습니다."),
    ANALYSIS_JOB_NOT_FOUND("분석 작업을 찾을 수 없습니다."),
    ANALYSIS_ALREADY_RUNNING("이미 분석 중인 작업입니다."),
    ANALYSIS_RETRY_NOT_ALLOWED("실패한 분석 작업만 재시도할 수 있습니다."),
    ANALYSIS_CANCEL_NOT_ALLOWED("완료 또는 실패한 분석 작업은 취소할 수 없습니다."),
    ANALYSIS_SOURCE_VIDEO_MISSING("원본 영상 파일이 없어 재시도할 수 없습니다. 영상을 다시 업로드해 주세요."),
    ANALYSIS_DELETE_NOT_ALLOWED("완료 또는 실패한 리포트만 삭제할 수 있습니다."),
    RESULT_NOT_FOUND("분석 결과를 찾을 수 없습니다."),
    PYTHON_ANALYSIS_ERROR("분석 서버 오류가 발생했습니다."),
    LLM_RESPONSE_ERROR("AI 피드백 응답 오류가 발생했습니다."),
    RESULT_SAVE_ERROR("결과 저장에 실패했습니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
