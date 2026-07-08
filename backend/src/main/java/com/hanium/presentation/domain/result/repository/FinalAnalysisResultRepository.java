// 역할: 도메인 엔티티를 조회하고 저장하는 Spring Data JPA 저장소입니다.
package com.hanium.presentation.domain.result.repository;

import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalAnalysisResultRepository extends JpaRepository<FinalAnalysisResult, Long> {
    Optional<FinalAnalysisResult> findByJobId(Long jobId);

    void deleteByJobId(Long jobId);

    List<FinalAnalysisResult> findByJob_User_IdOrderByCreatedAtDesc(Long userId);

    List<FinalAnalysisResult> findByJob_IdIn(Collection<Long> jobIds);
}
