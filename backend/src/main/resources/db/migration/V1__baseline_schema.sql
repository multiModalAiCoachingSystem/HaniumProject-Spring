-- Current application schema for a completely empty MySQL 8.4 database.
-- Existing databases are marked at version 1 and must not execute this file.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    email VARCHAR(255) NOT NULL,
    enabled BIT(1) NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'MANAGER', 'USER') NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BIT(1) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE uploaded_videos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    duration_seconds DOUBLE NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    relative_path VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_uploaded_videos_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE analysis_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    error_message VARCHAR(500) NULL,
    message VARCHAR(500) NULL,
    progress_percent INT NOT NULL,
    retry_count INT NOT NULL,
    started_at DATETIME(6) NULL,
    status ENUM(
        'CANCELED',
        'CANCEL_REQUESTED',
        'DONE',
        'FAILED',
        'PROCESSING',
        'QUEUED'
    ) NOT NULL,
    step ENUM(
        'AUDIO_ANALYSIS',
        'COMPLETED',
        'FAILED',
        'FEEDBACK_GENERATION',
        'PREPROCESSING',
        'QUEUED',
        'RESULT_MERGING',
        'SAVING_RESULT',
        'STT_ANALYSIS',
        'VIDEO_ANALYSIS',
        'VIDEO_LLM_ANALYSIS'
    ) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    last_recovery_at DATETIME(6) NULL,
    last_recovery_reason VARCHAR(80) NULL,
    recovery_attempt_count INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_analysis_jobs_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_analysis_jobs_video
        FOREIGN KEY (video_id) REFERENCES uploaded_videos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE final_analysis_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    feedback_json LONGTEXT NOT NULL,
    scores_json LONGTEXT NOT NULL,
    summary_json LONGTEXT NOT NULL,
    total_score DOUBLE NOT NULL,
    job_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_final_analysis_results_job UNIQUE (job_id),
    CONSTRAINT fk_final_results_job
        FOREIGN KEY (job_id) REFERENCES analysis_jobs (id),
    CONSTRAINT fk_final_results_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_final_results_video
        FOREIGN KEY (video_id) REFERENCES uploaded_videos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
