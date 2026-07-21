// 역할: 리포트 보관함 목록에 표시할 분석 작업과 결과 요약 정보를 담는 응답 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

import com.hanium.presentation.common.util.AnalysisFailureMessages;
import com.hanium.presentation.common.util.ApiDateTimes;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import java.time.OffsetDateTime;

public record AnalysisReportResponse(
        Long jobId,
        Long resultId,
        String title,
        String originalFileName,
        String status,
        int retryCount,
        Double totalScore,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String failureReason
) {
    public static AnalysisReportResponse from(AnalysisJob job, FinalAnalysisResult result) {
        boolean failed = job.getStatus() == AnalysisStatus.FAILED;
        Double score = result == null ? null : result.getTotalScore();
        String originalFileName = job.getVideo().getOriginalFilename();
        return new AnalysisReportResponse(
                job.getId(),
                result == null ? null : result.getId(),
                originalFileName,
                originalFileName,
                job.getStatus().name(),
                job.getRetryCount(),
                score,
                ApiDateTimes.inServiceZone(job.getCreatedAt()),
                failed ? null : ApiDateTimes.inServiceZone(job.getCompletedAt()),
                failed ? ApiDateTimes.inServiceZone(job.getCompletedAt()) : null,
                failed ? AnalysisFailureMessages.safeStoredMessage(firstPresent(job.getErrorMessage(), job.getMessage())) : null
        );
    }

    private static String firstPresent(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
