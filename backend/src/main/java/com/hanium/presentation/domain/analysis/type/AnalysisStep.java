// 역할: 도메인 상태, 역할, 분류 값을 제한하는 enum 타입입니다.
package com.hanium.presentation.domain.analysis.type;

public enum AnalysisStep {
    QUEUED, PREPROCESSING, AUDIO_ANALYSIS, VIDEO_ANALYSIS, STT_ANALYSIS, VIDEO_LLM_ANALYSIS, FEEDBACK_GENERATION, RESULT_MERGING, SAVING_RESULT, COMPLETED, FAILED
}
