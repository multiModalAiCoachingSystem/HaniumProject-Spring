// 역할: 데이터베이스 테이블과 매핑되는 핵심 도메인 엔티티입니다.
package com.hanium.presentation.domain.video.entity;

import com.hanium.presentation.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_videos")
public class UploadedVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String storedFilename;

    @Column(nullable = false)
    private String relativePath;

    @Column(nullable = false, length = 120)
    private String mimeType;

    @Column(nullable = false)
    private long fileSize;

    private Double durationSeconds;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 함수 역할: UploadedVideo에서 업로드 파일을 검증하고 저장 또는 응답 데이터를 구성합니다.
    protected UploadedVideo() {
    }

    // 함수 역할: UploadedVideo에서 업로드 파일을 검증하고 저장 또는 응답 데이터를 구성합니다.
    public UploadedVideo(User user, String originalFilename, String storedFilename, String relativePath, String mimeType, long fileSize) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.relativePath = relativePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    // 함수 역할: onCreate에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 함수 역할: getId에서 필요한 값이나 상태를 조회해 반환합니다.
    public Long getId() {
        return id;
    }

    // 함수 역할: getUser에서 필요한 값이나 상태를 조회해 반환합니다.
    public User getUser() {
        return user;
    }

    // 함수 역할: getOriginalFilename에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getOriginalFilename() {
        return originalFilename;
    }

    // 함수 역할: getStoredFilename에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getStoredFilename() {
        return storedFilename;
    }

    // 함수 역할: getRelativePath에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getRelativePath() {
        return relativePath;
    }

    // 함수 역할: getMimeType에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getMimeType() {
        return mimeType;
    }

    // 함수 역할: getFileSize에서 필요한 값이나 상태를 조회해 반환합니다.
    public long getFileSize() {
        return fileSize;
    }

    // 함수 역할: getDurationSeconds에서 필요한 값이나 상태를 조회해 반환합니다.
    public Double getDurationSeconds() {
        return durationSeconds;
    }

    // 함수 역할: getCreatedAt에서 필요한 값이나 상태를 조회해 반환합니다.
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
