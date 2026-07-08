// 역할: 도메인 엔티티를 조회하고 저장하는 Spring Data JPA 저장소입니다.
package com.hanium.presentation.domain.analysis.repository;

import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long>, JpaSpecificationExecutor<AnalysisJob> {
    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AnalysisJob> findByStatusOrderByCreatedAtDesc(AnalysisStatus status);

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
}
