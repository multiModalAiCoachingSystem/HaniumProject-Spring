// 역할: 클라이언트에 반환할 API 응답 데이터를 담는 응답 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ResultDetailResponse(Long resultId, Long jobId, Long videoId, String title, LocalDateTime analysisDate,
                                   String originalFilename, Double durationSeconds, double totalScore,
                                   List<Map<String, Object>> scores, List<Map<String, Object>> feedback,
                                   Map<String, Object> summary, Map<String, Object> metrics,
                                   Map<String, Object> videoLlmMetadata,
                                   List<Map<String, Object>> observations) {
}
