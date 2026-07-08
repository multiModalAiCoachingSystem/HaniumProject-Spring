-- 역할: MySQL 컨테이너 최초 실행 시 필요한 기본 스키마와 초기 데이터를 준비합니다.
CREATE DATABASE IF NOT EXISTS presentation_coaching_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE presentation_coaching_ai;

DELIMITER //
CREATE PROCEDURE ensure_final_analysis_result_json_columns()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'final_analysis_results'
    ) THEN
        ALTER TABLE final_analysis_results
            MODIFY COLUMN scores_json LONGTEXT NOT NULL,
            MODIFY COLUMN feedback_json LONGTEXT NOT NULL,
            MODIFY COLUMN summary_json LONGTEXT NOT NULL;
    END IF;
END //
DELIMITER ;

CALL ensure_final_analysis_result_json_columns();
DROP PROCEDURE ensure_final_analysis_result_json_columns;
