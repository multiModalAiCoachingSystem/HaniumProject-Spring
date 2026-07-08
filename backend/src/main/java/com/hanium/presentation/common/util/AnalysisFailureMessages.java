// 역할: 내부 예외 정보를 사용자에게 노출하지 않도록 안전한 분석 실패 메시지로 변환합니다.
package com.hanium.presentation.common.util;

import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import java.util.Locale;

public final class AnalysisFailureMessages {
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String VIDEO_FILE_NOT_FOUND = "VIDEO_FILE_NOT_FOUND";
    public static final String UNSUPPORTED_VIDEO_FORMAT = "UNSUPPORTED_VIDEO_FORMAT";
    public static final String ANALYSIS_ENGINE_ERROR = "ANALYSIS_ENGINE_ERROR";
    public static final String RESULT_SAVE_ERROR = "RESULT_SAVE_ERROR";

    private AnalysisFailureMessages() {
    }

    public static String codeFrom(Throwable throwable) {
        String businessCode = codeFromBusinessException(throwable);
        if (businessCode != null) {
            return businessCode;
        }

        String text = throwable == null ? "" : stackText(throwable).toLowerCase(Locale.ROOT);
        if (text.contains("video_not_found") || text.contains("no such file") || text.contains("filenotfound")) {
            return VIDEO_FILE_NOT_FOUND;
        }
        if (text.contains("invalid_video_type") || text.contains("unsupported") || text.contains("ffmpeg conversion failed")) {
            return UNSUPPORTED_VIDEO_FORMAT;
        }
        if (text.contains("data too long") || text.contains("final_analysis_results") || text.contains("feedback_json")
                || text.contains("could not execute statement") || text.contains("hibernate")) {
            return RESULT_SAVE_ERROR;
        }
        if (text.contains("analysis-engine") || text.contains("video-llm-engine") || text.contains("webclient")) {
            return ANALYSIS_ENGINE_ERROR;
        }
        return UNKNOWN_ERROR;
    }

    public static String messageOf(String code) {
        return switch (code) {
            case VIDEO_FILE_NOT_FOUND -> "원본 영상 파일을 찾을 수 없습니다.";
            case UNSUPPORTED_VIDEO_FORMAT -> "지원하지 않는 영상 형식입니다.";
            case ANALYSIS_ENGINE_ERROR -> "영상 분석 중 오류가 발생했습니다.";
            case RESULT_SAVE_ERROR -> "분석 결과 저장 중 오류가 발생했습니다.";
            default -> "분석 처리 중 오류가 발생했습니다.";
        };
    }

    public static String safeMessageFrom(Throwable throwable) {
        return messageOf(codeFrom(throwable));
    }

    public static String safeStoredMessage(String storedMessage) {
        if (storedMessage == null || storedMessage.isBlank()) {
            return messageOf(UNKNOWN_ERROR);
        }
        if (isKnownCode(storedMessage)) {
            return messageOf(storedMessage);
        }

        String lower = storedMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("insert into") || lower.contains("hibernate") || lower.contains("could not execute")
                || lower.contains("exception") || lower.contains("feedback_json") || lower.contains("final_analysis_results")
                || lower.contains("jdbc") || lower.contains("java.") || lower.contains("/app/") || lower.contains("\\app\\")
                || lower.contains("http://") || lower.contains("https://")) {
            return messageOf(UNKNOWN_ERROR);
        }
        return storedMessage;
    }

    public static String codeFromStoredMessage(String storedMessage) {
        if (storedMessage == null || storedMessage.isBlank()) {
            return UNKNOWN_ERROR;
        }
        if (isKnownCode(storedMessage)) {
            return storedMessage;
        }
        String lower = storedMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("no such file") || lower.contains("filenotfound")) {
            return VIDEO_FILE_NOT_FOUND;
        }
        if (lower.contains("unsupported") || lower.contains("ffmpeg conversion failed")) {
            return UNSUPPORTED_VIDEO_FORMAT;
        }
        if (lower.contains("data too long") || lower.contains("final_analysis_results") || lower.contains("feedback_json")
                || lower.contains("could not execute statement") || lower.contains("hibernate")) {
            return RESULT_SAVE_ERROR;
        }
        if (lower.contains("analysis-engine") || lower.contains("video-llm-engine") || lower.contains("webclient")) {
            return ANALYSIS_ENGINE_ERROR;
        }
        return UNKNOWN_ERROR;
    }

    private static boolean isKnownCode(String value) {
        return VIDEO_FILE_NOT_FOUND.equals(value)
                || UNSUPPORTED_VIDEO_FORMAT.equals(value)
                || ANALYSIS_ENGINE_ERROR.equals(value)
                || RESULT_SAVE_ERROR.equals(value)
                || UNKNOWN_ERROR.equals(value);
    }

    private static String stackText(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass() != null) {
                builder.append(current.getClass().getName()).append(' ');
            }
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private static String codeFromBusinessException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                ErrorCode errorCode = businessException.errorCode();
                if (errorCode == ErrorCode.VIDEO_NOT_FOUND || errorCode == ErrorCode.ANALYSIS_SOURCE_VIDEO_MISSING) {
                    return VIDEO_FILE_NOT_FOUND;
                }
                if (errorCode == ErrorCode.INVALID_VIDEO_TYPE) {
                    return UNSUPPORTED_VIDEO_FORMAT;
                }
                if (errorCode == ErrorCode.PYTHON_ANALYSIS_ERROR || errorCode == ErrorCode.LLM_RESPONSE_ERROR) {
                    return ANALYSIS_ENGINE_ERROR;
                }
                if (errorCode == ErrorCode.RESULT_SAVE_ERROR) {
                    return RESULT_SAVE_ERROR;
                }
            }
            current = current.getCause();
        }
        return null;
    }
}
