// 역할: 외부 분석 엔진과 주고받는 요청/응답 데이터 구조입니다.
package com.hanium.presentation.infrastructure.client.videollm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoLlmEngineResponse(
        Long jobId,
        Long videoId,
        String provider,
        String model,
        Map<String, Object> metadata,
        List<Map<String, Object>> sampledFrames,
        Map<String, Object> audio,
        List<Map<String, Object>> observations
) {
}
