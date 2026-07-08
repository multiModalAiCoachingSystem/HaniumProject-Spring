// 역할: 외부 분석 엔진과 주고받는 요청/응답 데이터 구조입니다.
package com.hanium.presentation.infrastructure.client.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisEngineResponse(
        Long jobId,
        Long videoId,
        Double durationSeconds,
        Map<String, Object> videoInfo,
        String transcript,
        Map<String, Object> stt,
        Map<String, Object> metrics,
        List<Map<String, Object>> scores,
        Double totalScore,
        Map<String, Object> summary,
        List<Map<String, Object>> observations
) {
}
