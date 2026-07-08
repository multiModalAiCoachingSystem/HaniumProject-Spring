// 역할: 클라이언트에 반환할 API 응답 데이터를 담는 응답 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

import com.hanium.presentation.domain.video.entity.UploadedVideo;
import java.time.LocalDateTime;

public record VideoResponse(Long videoId, String originalFilename, String mimeType, long fileSize,
                            Double durationSeconds, LocalDateTime createdAt) {
    // 함수 역할: from에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public static VideoResponse from(UploadedVideo video) {
        return new VideoResponse(video.getId(), video.getOriginalFilename(), video.getMimeType(),
                video.getFileSize(), video.getDurationSeconds(), video.getCreatedAt());
    }
}
