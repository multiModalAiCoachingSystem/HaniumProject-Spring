-- Indexes derived from current Spring Data repository and report query patterns.

CREATE INDEX idx_analysis_jobs_user_status_created
    ON analysis_jobs (user_id, status, created_at, id);

CREATE INDEX idx_analysis_jobs_status_created
    ON analysis_jobs (status, created_at, id);

CREATE INDEX idx_uploaded_videos_user_created
    ON uploaded_videos (user_id, created_at, id);

CREATE INDEX idx_refresh_tokens_user_revoked
    ON refresh_tokens (user_id, revoked);
