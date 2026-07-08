// 역할: 도메인 상태, 역할, 분류 값을 제한하는 enum 타입입니다.
package com.hanium.presentation.domain.analysis.type;

public enum AnalysisStatus {
    QUEUED, PROCESSING, CANCEL_REQUESTED, CANCELED, DONE, FAILED
}
