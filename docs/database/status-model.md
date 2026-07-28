# 상태값

## `AnalysisStatus`

정의 위치: `backend/src/main/java/com/hanium/presentation/domain/analysis/type/AnalysisStatus.java`

| 값 | 의미 | 현재 사용 여부 |
|---|---|---|
| `QUEUED` | 분석 대기 | 사용 |
| `PROCESSING` | 분석 진행 중 | 사용 |
| `CANCEL_REQUESTED` | 실행 중인 단계 종료 후 취소 요청 | 사용 |
| `DONE` | 분석 완료 | 사용 |
| `FAILED` | 분석 실패 | 사용 |
| `CANCELED` | 최종 취소 | 사용 |

## `AnalysisStep`

정의 위치: `backend/src/main/java/com/hanium/presentation/domain/analysis/type/AnalysisStep.java`

| 값 | 의미 | 현재 pipeline 사용 |
|---|---|---|
| `QUEUED` | 대기 | job 생성/재시도 |
| `PREPROCESSING` | 전처리 | 10% |
| `AUDIO_ANALYSIS` | 정량 분석 엔진 호출 | 30% 메시지에 음성/자세/시선/표정 포함 |
| `VIDEO_ANALYSIS` | 영상 분석 | enum만 존재 |
| `STT_ANALYSIS` | STT 분석 | enum만 존재 |
| `VIDEO_LLM_ANALYSIS` | video-llm-engine 호출 | 75% |
| `FEEDBACK_GENERATION` | 피드백 생성 | enum만 존재 |
| `RESULT_MERGING` | 결과 병합 | 90% |
| `SAVING_RESULT` | 결과 저장 | enum만 존재 |
| `COMPLETED` | 완료 | `done()`에서 사용 |
| `FAILED` | 실패 | `fail()`에서 사용 |

## `UserRole`

정의 위치: `backend/src/main/java/com/hanium/presentation/domain/user/type/UserRole.java`

| 값 | 우선순위 | 권한 |
|---|---:|---|
| `USER` | 1 | 일반 기능 |
| `MANAGER` | 2 | 운영 콘솔 접근과 조회. 임의 사용자 분석 삭제의 소유권 생략 대상은 아님 |
| `ADMIN` | 3 | 운영 콘솔 접근, 사용자 권한 변경/추방, 전체 결과 접근 |

## `FeedbackCategory`

정의 위치: `backend/src/main/java/com/hanium/presentation/domain/result/type/FeedbackCategory.java`

`SPEECH_SPEED`, `FILLER_WORD`, `PAUSE`, `PRONUNCIATION`, `EYE_CONTACT`, `POSTURE`, `GESTURE`, `LOGIC`

현재 analysis-engine의 점수 category는 `POSTURE`, `EXPRESSION`, `EYE_CONTACT`, `VOICE`, `GESTURE`를 사용한다. 백엔드 enum과 완전히 일치하지 않는다.

## 재시도 시 변경되는 컬럼

`AnalysisJob.retry()`가 다음 컬럼을 변경한다.

| 컬럼 | 재시도 값 |
|---|---|
| `status` | `QUEUED` |
| `step` | `QUEUED` |
| `progress_percent` | 0 |
| `retry_count` | 기존 값 + 1 |
| `message` | `분석 재시도 대기 중입니다.` |
| `error_message` | null |
| `started_at` | null |
| `completed_at` | null |

재시도 전에 기존 `final_analysis_results`는 삭제된다. 파일 시스템의 이전 분석 산출물도 commit 이후 삭제된다.

재시도 허용 상태는 `FAILED`, `CANCELED`이며 동일 `jobId`를 유지한다. 삭제 허용 상태는 `DONE`, `FAILED`, `CANCELED`이다.
