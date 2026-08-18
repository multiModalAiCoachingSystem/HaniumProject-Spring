package com.hanium.presentation.global.exception;

public class AnalysisAlreadyActiveException extends BusinessException {
    private final Long jobId;

    public AnalysisAlreadyActiveException(Long jobId) {
        super(ErrorCode.ANALYSIS_ALREADY_ACTIVE);
        this.jobId = jobId;
    }

    public Long jobId() {
        return jobId;
    }
}
