// 역할: 도메인 엔티티를 조회하고 저장하는 Spring Data JPA 저장소입니다.
package com.hanium.presentation.domain.video.repository;

import com.hanium.presentation.domain.video.entity.UploadedVideo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedVideoRepository extends JpaRepository<UploadedVideo, Long> {
    List<UploadedVideo> findByUserIdOrderByCreatedAtDesc(Long userId);
}
