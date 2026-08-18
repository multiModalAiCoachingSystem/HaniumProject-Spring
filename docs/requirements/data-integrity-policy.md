# 데이터 무결성 요구사항 개정

이 문서는 `01_요구사항명세서.pdf`의 재시도, 영상 길이, 활성 작업, 최종 결과 유일성 기준을 구체화한다. 해당 항목이 기존 명세와 충돌하면 이 개정 정책을 우선한다.

## 영상과 Job 생성

| 동작 | videoId | jobId | 결과 |
|---|---|---|---|
| 새 영상 업로드 | 새로 생성 | 아직 없음 | ffprobe 검증 후 `uploaded_videos` 저장 |
| 최초 분석 시작 | 기존 값 | 새로 생성 | 초기 상태 `QUEUED` |
| `FAILED` 재시도 | 유지 | 유지 | 기존 결과와 부분 산출물 정리 후 `QUEUED` |
| `CANCELED` 재시도 | 유지 | 유지 | 기존 결과와 부분 산출물 정리 후 `QUEUED` |
| `DONE` retry 요청 | 유지 | 유지 | 409 `ANALYSIS_RETRY_NOT_ALLOWED` |
| 종료된 영상의 별도 재분석 | 유지 | 새로 생성 | 최초 분석 API로 새 분석 이력 생성 |

`FAILED`와 `CANCELED` 재시도는 동일 분석 이력의 재실행이다. 따라서 `retry_count`를 증가시키고 동일 `jobId`를 유지한다. 새 영상을 업로드하면 항상 새 `videoId`를 사용한다.

## 영상 길이

- 기본 최소 길이: 5초 (`VIDEO_MIN_DURATION_SECONDS`)
- 기본 최대 길이: 600초 (`VIDEO_MAX_DURATION_SECONDS`)
- 판정 근거: 업로드 파일의 실제 ffprobe metadata
- 5초 미만: `VIDEO_TOO_SHORT`
- 600초 초과: `VIDEO_TOO_LONG`
- metadata 판독 실패: `INVALID_VIDEO_METADATA`

검증 실패 파일은 분석 엔진에 전달하지 않는다. DB row 생성 전 실패하면 저장 파일만 정리하고, DB 저장 실패 시 트랜잭션 rollback과 파일 정리를 함께 수행한다.

## 활성 Job 유일성

같은 `video_id`에는 다음 활성 상태 job을 합쳐 최대 하나만 허용한다.

- `QUEUED`
- `PROCESSING`
- `CANCEL_REQUESTED`

중복 요청은 409 `ANALYSIS_ALREADY_ACTIVE`와 기존 활성 `jobId`를 반환한다. 애플리케이션은 `uploaded_videos` row의 `PESSIMISTIC_WRITE` 락으로 시작·재시도 요청을 직렬화한다. MySQL은 V3 generated column과 `uk_analysis_jobs_active_video` unique 제약으로 race condition을 최종 차단한다.

## 최종 결과 1:1 기준

최종 관계는 `AnalysisJob 1 : 0..1 FinalAnalysisResult`이다.

- 유일성 기준: `final_analysis_results.job_id UNIQUE`
- 조회 편의 FK: `final_analysis_results.video_id`
- `video_id` unique: 사용하지 않음

동일 job의 결과를 다시 저장할 때는 기존 결과를 먼저 제거한 뒤 저장하며, DB unique 제약이 중복 row를 방지한다. 하나의 영상은 여러 종료 분석 이력을 가질 수 있으므로 각 job별 결과가 존재할 수 있다.

## 기존 데이터 처리

V3 적용 전에 활성 job 중복을 검사한다. 중복이 있으면 자동 삭제하지 않고 migration을 중단해 운영자가 확인한다. 이번 개정은 기존 영상, job, 결과 row를 삭제하거나 `jobId`를 변경하지 않는다.
