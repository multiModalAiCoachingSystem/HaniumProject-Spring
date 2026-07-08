package com.hanium.presentation.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalysisFailureMessagesTest {
    @Test
    void safeMessageFromDoesNotExposeSqlJavaClassOrServerPath() {
        RuntimeException exception = new RuntimeException(
                "could not execute statement: insert into final_analysis_results feedback_json at /app/backend/src/Main.java",
                new IllegalStateException("java.sql.SQLException: data too long")
        );

        String message = AnalysisFailureMessages.safeMessageFrom(exception);

        assertThat(message)
                .doesNotContainIgnoringCase("insert into")
                .doesNotContainIgnoringCase("java.")
                .doesNotContain("/app/")
                .doesNotContainIgnoringCase("feedback_json");
        assertThat(AnalysisFailureMessages.codeFrom(exception)).isEqualTo(AnalysisFailureMessages.RESULT_SAVE_ERROR);
    }

    @Test
    void safeStoredMessageHidesInternalDetails() {
        String stored = "java.lang.IllegalStateException at C:\\app\\backend\\Service.java: insert into feedback_json";

        String message = AnalysisFailureMessages.safeStoredMessage(stored);

        assertThat(message)
                .doesNotContainIgnoringCase("java.")
                .doesNotContainIgnoringCase("insert into")
                .doesNotContainIgnoringCase("feedback_json")
                .doesNotContain("\\app\\");
    }
}
