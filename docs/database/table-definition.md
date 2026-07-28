# 테이블 정의

테이블 구조는 Flyway `V1__baseline_schema.sql`과 JPA Entity 기준이다. `infra/mysql/init.sql`은 문자셋만 설정하며 애플리케이션 테이블 생성과 변경은 Backend Flyway가 담당한다.

## `users`

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `id` | BIGINT PK, auto increment | 사용자 ID |
| `email` | VARCHAR(255), NOT NULL, UNIQUE | 로그인 이메일 |
| `password` | VARCHAR(255), NOT NULL | BCrypt 해시 비밀번호 |
| `nickname` | VARCHAR(80), NOT NULL | 표시 이름 |
| `role` | VARCHAR(20), NOT NULL | `USER`, `MANAGER`, `ADMIN` |
| `enabled` | BOOLEAN, NOT NULL | 추방 시 false |
| `created_at` | DATETIME, NOT NULL, updatable=false | 생성 시각 |
| `updated_at` | DATETIME, NOT NULL | 수정 시각 |

## `refresh_tokens`

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `id` | BIGINT PK, auto increment | 토큰 ID |
| `user_id` | BIGINT FK, NOT NULL | `users.id` |
| `token_hash` | VARCHAR(128), NOT NULL, UNIQUE | refresh token SHA-256 해시 |
| `expires_at` | DATETIME, NOT NULL | 만료 시각 |
| `revoked` | BOOLEAN, NOT NULL | 로그아웃/추방 시 true |
| `created_at` | DATETIME, NOT NULL, updatable=false | 생성 시각 |

## `uploaded_videos`

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `id` | BIGINT PK, auto increment | 영상 ID |
| `user_id` | BIGINT FK, NOT NULL | 업로드 사용자 |
| `original_filename` | VARCHAR(255), NOT NULL | 정리된 원본 파일명 |
| `stored_filename` | VARCHAR(255), NOT NULL | UUID 기반 저장 파일명 |
| `relative_path` | VARCHAR(255), NOT NULL | `STORAGE_ROOT` 기준 상대 경로 |
| `mime_type` | VARCHAR(120), NOT NULL | 업로드 MIME 타입 |
| `file_size` | BIGINT, NOT NULL | 파일 크기 byte |
| `duration_seconds` | DOUBLE, NULL | 현재 업로드 시 채우지 않음 |
| `created_at` | DATETIME, NOT NULL, updatable=false | 생성 시각 |

## `analysis_jobs`

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `id` | BIGINT PK, auto increment | jobId |
| `user_id` | BIGINT FK, NOT NULL | 요청 사용자 |
| `video_id` | BIGINT FK, NOT NULL | 분석 대상 영상 |
| `status` | VARCHAR(30), NOT NULL | `AnalysisStatus` |
| `step` | VARCHAR(40), NOT NULL | `AnalysisStep` |
| `progress_percent` | INT, NOT NULL | 진행률 |
| `retry_count` | INT, NOT NULL | 재시도 횟수 |
| `recovery_attempt_count` | INT, NOT NULL | backend 재시작 자동 복구 시도 횟수 |
| `last_recovery_at` | DATETIME, NULL | 마지막 자동 복구 시각 |
| `last_recovery_reason` | VARCHAR(80), NULL | `STARTUP_QUEUED` 등 내부 복구 사유 코드 |
| `message` | VARCHAR(500), NULL | 사용자 표시 메시지 |
| `error_message` | VARCHAR(500), NULL | 안전한 실패 메시지 |
| `created_at` | DATETIME, NOT NULL, updatable=false | 생성 시각 |
| `started_at` | DATETIME, NULL | 처음 processing 진입 시각 |
| `completed_at` | DATETIME, NULL | 완료/실패 시각 |
| `updated_at` | DATETIME, NOT NULL | 수정 시각 |

## `final_analysis_results`

| 컬럼 | 타입/제약 | 설명 |
|---|---|---|
| `id` | BIGINT PK, auto increment | 결과 ID |
| `job_id` | BIGINT FK, NOT NULL, UNIQUE | `analysis_jobs.id` |
| `user_id` | BIGINT FK, NOT NULL | 결과 소유 사용자 |
| `video_id` | BIGINT FK, NOT NULL | 원본 영상 |
| `total_score` | DOUBLE, NOT NULL | analysis-engine 최종 점수 |
| `scores_json` | LONGTEXT, NOT NULL | 항목별 점수 JSON 문자열 |
| `feedback_json` | LONGTEXT, NOT NULL | basic/video LLM observation JSON 문자열 |
| `summary_json` | LONGTEXT, NOT NULL | transcript, stt, metrics, metadata JSON 문자열 |
| `created_at` | DATETIME, NOT NULL, updatable=false | 생성 시각 |

## 삭제 순서와 연관관계

리포트 삭제 시 `final_analysis_results`를 먼저 삭제하고 `analysis_jobs`를 삭제한다. 같은 영상에 연결된 job이 더 없으면 `uploaded_videos`와 실제 파일도 삭제한다. 사용자를 물리 삭제하는 API는 없고, 추방은 `users.enabled=false`와 refresh token revoke로 처리한다.

## JSON/LONGTEXT 컬럼

`scores_json`, `feedback_json`, `summary_json`은 JPA에서 `@Lob` 및 `columnDefinition = "LONGTEXT"`로 선언되어 있다. API 응답 시 `ObjectMapper`로 다시 `List<Map<String,Object>>` 또는 `Map<String,Object>`로 파싱한다.

Hibernate는 모든 프로필에서 `ddl-auto=validate`를 사용하며 스키마를 자동 변경하지 않는다. 기존 migration은 수정하지 않고 새 변경은 다음 `V{n}__description.sql`로 추가한다. recovery 컬럼이 없는 Flyway 이전 볼륨의 전환 절차는 `infra/mysql/migrations/20260724_add_analysis_recovery_columns.sql`과 [Flyway 운영 문서](flyway-migrations.md)를 따른다.
