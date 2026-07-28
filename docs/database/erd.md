# ERD

현재 실제 영속 엔티티는 `User`, `RefreshToken`, `UploadedVideo`, `AnalysisJob`, `FinalAnalysisResult`이다. `BasicAnalysisResult`, `VideoLlmAnalysisResult`, `OpenAiFeedbackResult`는 record/interface placeholder만 있고 JPA 엔티티가 아니므로 테이블로 구현되어 있지 않다.

```mermaid
erDiagram
    users ||--o{ refresh_tokens : has
    users ||--o{ uploaded_videos : uploads
    users ||--o{ analysis_jobs : requests
    uploaded_videos ||--o{ analysis_jobs : analyzed_by
    analysis_jobs ||--o| final_analysis_results : produces
    users ||--o{ final_analysis_results : owns
    uploaded_videos ||--o{ final_analysis_results : source

    users {
        bigint id PK
        varchar email UK
        varchar password
        varchar nickname
        varchar role
        boolean enabled
        datetime created_at
        datetime updated_at
    }

    refresh_tokens {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK
        datetime expires_at
        boolean revoked
        datetime created_at
    }

    uploaded_videos {
        bigint id PK
        bigint user_id FK
        varchar original_filename
        varchar stored_filename
        varchar relative_path
        varchar mime_type
        bigint file_size
        double duration_seconds
        datetime created_at
    }

    analysis_jobs {
        bigint id PK
        bigint user_id FK
        bigint video_id FK
        varchar status
        varchar step
        int progress_percent
        int retry_count
        int recovery_attempt_count
        datetime last_recovery_at
        varchar last_recovery_reason
        varchar message
        varchar error_message
        datetime created_at
        datetime started_at
        datetime completed_at
        datetime updated_at
    }

    final_analysis_results {
        bigint id PK
        bigint job_id FK UK
        bigint user_id FK
        bigint video_id FK
        double total_score
        longtext scores_json
        longtext feedback_json
        longtext summary_json
        datetime created_at
    }
```

## 관계

- `analysis_jobs.job_id`라는 별도 문자열 ID는 없고 PK `id`를 `jobId`로 사용한다.
- `final_analysis_results.job_id`는 `unique=true`이므로 하나의 분석 작업은 최종 결과를 하나만 가진다.
- 결과 삭제 시 `FinalAnalysisResultRepository.deleteByJobId()` 후 `AnalysisJobRepository.delete()` 순서로 삭제한다.

## 인덱스

V1 unique 제약:

- `users.email`
- `refresh_tokens.token_hash`
- `final_analysis_results.job_id`

V2 query 인덱스:

- `analysis_jobs(user_id, status, created_at, id)`
- `analysis_jobs(status, created_at, id)`
- `uploaded_videos(user_id, created_at, id)`
- `refresh_tokens(user_id, revoked)`
