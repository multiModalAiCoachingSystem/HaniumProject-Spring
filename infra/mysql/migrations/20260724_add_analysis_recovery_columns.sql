-- Existing MySQL volumes: run once before enabling the prod profile with ddl-auto=validate.
SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @schema_name AND table_name = 'analysis_jobs'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'analysis_jobs'
           AND column_name = 'recovery_attempt_count'
    ),
    'ALTER TABLE analysis_jobs ADD COLUMN recovery_attempt_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @schema_name AND table_name = 'analysis_jobs'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'analysis_jobs'
           AND column_name = 'last_recovery_at'
    ),
    'ALTER TABLE analysis_jobs ADD COLUMN last_recovery_at DATETIME(6) NULL',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @schema_name AND table_name = 'analysis_jobs'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @schema_name
           AND table_name = 'analysis_jobs'
           AND column_name = 'last_recovery_reason'
    ),
    'ALTER TABLE analysis_jobs ADD COLUMN last_recovery_reason VARCHAR(80) NULL',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;
