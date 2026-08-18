-- Active analysis states are mutually exclusive per uploaded video.
-- Terminal states generate NULL, and MySQL permits multiple NULL values in a UNIQUE index.
ALTER TABLE analysis_jobs
    ADD COLUMN active_video_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('QUEUED', 'PROCESSING', 'CANCEL_REQUESTED') THEN video_id
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT uk_analysis_jobs_active_video UNIQUE (active_video_id);
