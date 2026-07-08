// 역할: 실패한 분석 작업 재시도 요청의 응답 데이터를 담는 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

public record AnalysisRetryResponse(Long jobId, String status, String message) {
}
