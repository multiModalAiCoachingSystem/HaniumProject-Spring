// 역할: 클라이언트 요청 바디를 검증 가능한 객체로 전달하는 요청 DTO입니다.
package com.hanium.presentation.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record AnalysisRunRequest(@NotNull Long videoId) {
}
