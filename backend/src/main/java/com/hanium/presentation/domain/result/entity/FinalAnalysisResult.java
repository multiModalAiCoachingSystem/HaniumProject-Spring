// 역할: 데이터베이스 테이블과 매핑되는 핵심 도메인 엔티티입니다.
package com.hanium.presentation.domain.result.entity;

import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "final_analysis_results")
public class FinalAnalysisResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private AnalysisJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private UploadedVideo video;

    @Column(nullable = false)
    private double totalScore;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String scoresJson;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String feedbackJson;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String summaryJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 함수 역할: FinalAnalysisResult에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    protected FinalAnalysisResult() {
    }

    // 함수 역할: FinalAnalysisResult에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    public FinalAnalysisResult(AnalysisJob job, double totalScore, String scoresJson, String feedbackJson,
            String summaryJson) {
        this.job = job;
        this.user = job.getUser();
        this.video = job.getVideo();
        this.totalScore = totalScore;
        this.scoresJson = scoresJson;
        this.feedbackJson = feedbackJson;
        this.summaryJson = summaryJson;
    }

    // 함수 역할: onCreate에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public Long getId() {
        return id;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public AnalysisJob getJob() {
        return job;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public User getUser() {
        return user;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public UploadedVideo getVideo() {
        return video;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public double getTotalScore() {
        return totalScore;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public String getScoresJson() {
        return scoresJson;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public String getFeedbackJson() {
        return feedbackJson;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public String getSummaryJson() {
        return summaryJson;
    }

    // 함수 역할: 현재 객체가 가진 값을 조회해 반환합니다.
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
