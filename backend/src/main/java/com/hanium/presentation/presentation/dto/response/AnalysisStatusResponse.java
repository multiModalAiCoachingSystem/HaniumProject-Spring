// 역할: 클라이언트에 반환할 API 응답 데이터를 담는 응답 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

import com.hanium.presentation.common.util.AnalysisFailureMessages;
import com.hanium.presentation.common.util.ApiDateTimes;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record AnalysisStatusResponse(Long jobId, Long videoId, String originalFilename,
                                     String status, String step, int progressPercent, String message,
                                     OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt,
                                     Long elapsedSeconds, String errorMessage) {
    // 함수 역할: from에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public static AnalysisStatusResponse from(AnalysisJob job) {
        String errorMessage = job.getStatus() == AnalysisStatus.FAILED
                ? AnalysisFailureMessages.safeStoredMessage(job.getErrorMessage())
                : null;
        return new AnalysisStatusResponse(job.getId(), job.getVideo().getId(), job.getVideo().getOriginalFilename(),
                job.getStatus().name(), job.getStep().name(),
                job.getProgressPercent(), job.getMessage(), ApiDateTimes.inServiceZone(job.getCreatedAt()),
                ApiDateTimes.inServiceZone(job.getStartedAt()), ApiDateTimes.inServiceZone(job.getCompletedAt()),
                elapsedSeconds(job), errorMessage);
    }

    private static Long elapsedSeconds(AnalysisJob job) {
        LocalDateTime startedAt = job.getStartedAt();
        if (startedAt == null) {
            return null;
        }

        LocalDateTime endedAt = job.getCompletedAt() == null ? LocalDateTime.now() : job.getCompletedAt();
        return Math.max(0, Duration.between(startedAt, endedAt).getSeconds());
    }
}
