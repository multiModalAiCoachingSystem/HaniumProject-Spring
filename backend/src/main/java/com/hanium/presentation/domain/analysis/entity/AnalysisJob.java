package com.hanium.presentation.domain.analysis.entity;

import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.analysis.type.AnalysisStep;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private UploadedVideo video;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AnalysisStep step;

    @Column(nullable = false)
    private int progressPercent;

    @Column(nullable = false)
    private int retryCount;

    @Column(length = 500)
    private String message;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AnalysisJob() {
    }

    public AnalysisJob(User user, UploadedVideo video) {
        this.user = user;
        this.video = video;
        this.status = AnalysisStatus.QUEUED;
        this.step = AnalysisStep.QUEUED;
        this.progressPercent = 0;
        this.retryCount = 0;
        this.message = "분석 대기 중입니다.";
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean processing(AnalysisStep step, int progressPercent, String message) {
        if (isCancellationStatus()) {
            return false;
        }
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        this.status = AnalysisStatus.PROCESSING;
        this.step = step;
        this.progressPercent = progressPercent;
        this.message = message;
        return true;
    }

    public boolean done() {
        if (isCancellationStatus()) {
            return false;
        }
        this.status = AnalysisStatus.DONE;
        this.step = AnalysisStep.COMPLETED;
        this.progressPercent = 100;
        this.message = "분석이 완료되었습니다.";
        this.completedAt = LocalDateTime.now();
        return true;
    }

    public boolean fail(String message) {
        if (isCancellationStatus()) {
            return false;
        }
        this.status = AnalysisStatus.FAILED;
        this.step = AnalysisStep.FAILED;
        this.errorMessage = message;
        this.message = "분석에 실패했습니다.";
        this.completedAt = LocalDateTime.now();
        return true;
    }

    public void requestCancel() {
        if (this.status == AnalysisStatus.PROCESSING) {
            this.status = AnalysisStatus.CANCEL_REQUESTED;
            this.message = "분석 중단을 요청했습니다.";
        }
    }

    public void cancel() {
        this.status = AnalysisStatus.CANCELED;
        this.message = "분석이 취소되었습니다.";
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCancellationStatus() {
        return this.status == AnalysisStatus.CANCEL_REQUESTED || this.status == AnalysisStatus.CANCELED;
    }

    public void retry() {
        this.status = AnalysisStatus.QUEUED;
        this.step = AnalysisStep.QUEUED;
        this.progressPercent = 0;
        this.retryCount += 1;
        this.message = "분석 재시도 대기 중입니다.";
        this.errorMessage = null;
        this.startedAt = null;
        this.completedAt = null;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public UploadedVideo getVideo() { return video; }
    public AnalysisStatus getStatus() { return status; }
    public AnalysisStep getStep() { return step; }
    public int getProgressPercent() { return progressPercent; }
    public int getRetryCount() { return retryCount; }
    public String getMessage() { return message; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
