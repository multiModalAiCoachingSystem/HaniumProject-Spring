// 역할: 도메인 엔티티를 조회하고 저장하는 Spring Data JPA 저장소입니다.
package com.hanium.presentation.domain.video.repository;

import com.hanium.presentation.domain.video.entity.UploadedVideo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadedVideoRepository extends JpaRepository<UploadedVideo, Long> {
    List<UploadedVideo> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select video from UploadedVideo video join fetch video.user where video.id = :videoId")
    Optional<UploadedVideo> findByIdForUpdate(@Param("videoId") Long videoId);
}
