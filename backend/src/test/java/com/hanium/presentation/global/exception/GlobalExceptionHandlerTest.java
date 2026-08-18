package com.hanium.presentation.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void activeAnalysisConflictIncludesExistingJobId() {
        var response = handler.handleAnalysisAlreadyActive(new AnalysisAlreadyActiveException(100L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("ANALYSIS_ALREADY_ACTIVE");
        assertThat(response.getBody().data()).containsEntry("jobId", 100L);
    }
}
