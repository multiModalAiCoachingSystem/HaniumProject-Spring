# 분석 파이프라인

## 시작 흐름

1. 사용자가 `/upload`에서 영상을 선택한다.
2. 프론트엔드가 `POST /api/videos`로 파일을 업로드한다.
3. 프론트엔드가 응답의 `videoId`로 `POST /api/analyses`를 호출한다.
4. 백엔드가 `AnalysisJob`을 `QUEUED` 상태로 저장한다.
5. 트랜잭션 commit 이후 `AnalysisPipelineService.runAnalysis(jobId)`가 `@Async("analysisTaskExecutor")`로 실행된다.

```mermaid
sequenceDiagram
    participant UI as React UploadPage
    participant BE as Spring Boot
    participant DB as MySQL
    participant AE as analysis-engine
    participant VLE as video-llm-engine

    UI->>BE: POST /api/videos multipart file
    BE->>DB: uploaded_videos 저장
    BE-->>UI: videoId
    UI->>BE: POST /api/analyses {videoId}
    BE->>DB: analysis_jobs QUEUED 저장
    BE-->>UI: jobId, QUEUED
    BE->>BE: afterCommit async runAnalysis
    BE->>DB: PROCESSING/PREPROCESSING 10%
    BE->>AE: POST /api/basic-analysis
    AE-->>BE: metrics, scores, totalScore
    BE->>DB: PROCESSING/VIDEO_LLM_ANALYSIS 75%
    BE->>VLE: POST /api/video-llm-analysis
    VLE-->>BE: observations, provider metadata
    BE->>BE: qwen3:4b 한국어 피드백 또는 fallback
    BE->>DB: final_analysis_results 저장
    BE->>DB: DONE/COMPLETED 100%
```

## 상태 단계

`AnalysisStep` enum 값은 다음과 같다.

`QUEUED`, `PREPROCESSING`, `AUDIO_ANALYSIS`, `VIDEO_ANALYSIS`, `STT_ANALYSIS`, `VIDEO_LLM_ANALYSIS`, `FEEDBACK_GENERATION`, `RESULT_MERGING`, `SAVING_RESULT`, `COMPLETED`, `FAILED`

현재 실제 pipeline에서 사용하는 단계는 `PREPROCESSING`, `AUDIO_ANALYSIS`, `VIDEO_LLM_ANALYSIS`, `RESULT_MERGING`, `COMPLETED`, `FAILED`이다. `VIDEO_ANALYSIS`, `STT_ANALYSIS`, `FEEDBACK_GENERATION`, `SAVING_RESULT`는 enum에는 있으나 현재 `runAnalysis()`에서 별도 update로 사용하지 않는다.

## 결과 병합

`AnalysisPipelineService.saveEngineResult()`는 다음을 저장한다.

- `scores_json`: analysis-engine의 `scores`
- `feedback_json`: analysis-engine `observations` + video-llm-engine `observations`
- `summary_json`: analysis-engine `summary`에 transcript, stt, metrics, videoInfo, videoLlm metadata, sampledFrames, extractedAudio 병합
- `total_score`: analysis-engine의 `totalScore`

## 프론트엔드 polling

`UploadPage.jsx`와 `ResultListPage.jsx`는 `QUEUED`, `PROCESSING`, `CANCEL_REQUESTED` 상태일 때 4초 간격으로 상태/리포트를 다시 조회한다. `DONE`, `FAILED`, `CANCELED`에서는 polling을 중단한다. `useAnalysisStatus.js`는 2초 polling 훅이지만 현재 status 페이지는 `/upload`로 redirect하는 구조이다.

## 취소와 재시작 보호

- `QUEUED` 취소는 즉시 `CANCELED`로 바뀐다.
- `PROCESSING` 취소는 `CANCEL_REQUESTED`가 되고 pipeline의 단계별 확인 지점에서 `CANCELED`로 확정된다.
- 취소 상태는 결과 저장, `DONE`, `FAILED`로 덮어쓰지 않는다.
- `FAILED`, `CANCELED` 재분석은 같은 `jobId`를 유지하고 기존 결과와 부분 산출물을 정리한 뒤 `QUEUED`로 전환한다.
- backend 시작 시 recovery service가 남은 `QUEUED`, `PROCESSING`, `CANCEL_REQUESTED`를 조건부 UPDATE로 claim해 복구한다.
