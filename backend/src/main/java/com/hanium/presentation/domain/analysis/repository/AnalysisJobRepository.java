// 역할: 도메인 엔티티를 조회하고 저장하는 Spring Data JPA 저장소입니다.
package com.hanium.presentation.domain.analysis.repository;

import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long>, JpaSpecificationExecutor<AnalysisJob> {
    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AnalysisJob> findByStatusOrderByCreatedAtDesc(AnalysisStatus status);

    @EntityGraph(attributePaths = {"video"})
    List<AnalysisJob> findByStatusInOrderByCreatedAtAsc(List<AnalysisStatus> statuses);

    @Query("select job from AnalysisJob job join fetch job.video join fetch job.user where job.id = :jobId")
    Optional<AnalysisJob> findByIdWithVideoAndUser(@Param("jobId") Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AnalysisJob job join fetch job.video join fetch job.user where job.id = :jobId")
    Optional<AnalysisJob> findByIdWithVideoAndUserForUpdate(@Param("jobId") Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AnalysisJob job where job.id = :jobId")
    Optional<AnalysisJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @Override
    @EntityGraph(attributePaths = {"video"})
    Page<AnalysisJob> findAll(@Nullable Specification<AnalysisJob> specification, Pageable pageable);

    long countByVideoId(Long videoId);

    Optional<AnalysisJob> findFirstByVideoIdAndStatusInOrderByCreatedAtAsc(
            Long videoId,
            Collection<AnalysisStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE analysis_jobs
               SET status = 'PROCESSING',
                   step = 'PREPROCESSING',
                   progress_percent = 10,
                   message = '영상 분석을 준비하고 있습니다.',
                   error_message = NULL,
                   started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                   completed_at = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :jobId
               AND status = 'QUEUED'
            """, nativeQuery = true)
    int claimQueuedForProcessing(@Param("jobId") Long jobId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE analysis_jobs
               SET status = 'PROCESSING',
                   step = 'PREPROCESSING',
                   progress_percent = 10,
                   message = '영상 분석을 준비하고 있습니다.',
                   error_message = NULL,
                   started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                   completed_at = NULL,
                   recovery_attempt_count = recovery_attempt_count + 1,
                   last_recovery_at = CURRENT_TIMESTAMP,
                   last_recovery_reason = :reason,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :jobId
               AND status = 'QUEUED'
               AND recovery_attempt_count = :expectedAttemptCount
               AND recovery_attempt_count < :maxAttempts
            """, nativeQuery = true)
    int claimQueuedForRecovery(@Param("jobId") Long jobId,
                               @Param("expectedAttemptCount") int expectedAttemptCount,
                               @Param("maxAttempts") int maxAttempts,
                               @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE analysis_jobs
               SET status = 'QUEUED',
                   step = 'QUEUED',
                   progress_percent = 0,
                   message = '재시작 후 분석 복구 대기 중입니다.',
                   error_message = NULL,
                   started_at = NULL,
                   completed_at = NULL,
                   recovery_attempt_count = recovery_attempt_count + 1,
                   last_recovery_at = CURRENT_TIMESTAMP,
                   last_recovery_reason = :reason,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :jobId
               AND status = 'PROCESSING'
               AND recovery_attempt_count = :expectedAttemptCount
               AND recovery_attempt_count < :maxAttempts
            """, nativeQuery = true)
    int requeueProcessingForRecovery(@Param("jobId") Long jobId,
                                     @Param("expectedAttemptCount") int expectedAttemptCount,
                                     @Param("maxAttempts") int maxAttempts,
                                     @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE analysis_jobs
               SET status = 'CANCELED',
                   message = '분석이 취소되었습니다.',
                   completed_at = CURRENT_TIMESTAMP,
                   last_recovery_at = CURRENT_TIMESTAMP,
                   last_recovery_reason = :reason,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :jobId
               AND status = 'CANCEL_REQUESTED'
            """, nativeQuery = true)
    int finalizeCancelRequestedForRecovery(@Param("jobId") Long jobId,
                                           @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE analysis_jobs
               SET status = 'FAILED',
                   step = 'FAILED',
                   message = '분석에 실패했습니다.',
                   error_message = :safeMessage,
                   completed_at = CURRENT_TIMESTAMP,
                   last_recovery_at = CURRENT_TIMESTAMP,
                   last_recovery_reason = :reason,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :jobId
               AND status = :expectedStatus
            """, nativeQuery = true)
    int failRecoverableJob(@Param("jobId") Long jobId,
                           @Param("expectedStatus") String expectedStatus,
                           @Param("safeMessage") String safeMessage,
                           @Param("reason") String reason);
}
