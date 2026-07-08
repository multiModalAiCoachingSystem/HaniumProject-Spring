// 역할: 외부 분석 엔진과 주고받는 요청/응답 데이터 구조입니다.
package com.hanium.presentation.infrastructure.client.analysis.dto;

public record AnalysisEngineRequest(
        Long jobId,
        Long videoId,
        String filePath,
        String transcript
) {
    // 함수 역할: AnalysisEngineRequest에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    public AnalysisEngineRequest(Long jobId, Long videoId, String filePath) {
        this(jobId, videoId, filePath, null);
    }
}
