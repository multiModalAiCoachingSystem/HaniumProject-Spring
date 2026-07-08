// 역할: 데이터베이스 테이블과 매핑되는 핵심 도메인 엔티티입니다.
package com.hanium.presentation.domain.auth.entity;

import com.hanium.presentation.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 함수 역할: RefreshToken에서 인증 토큰 생성, 검증, 재발급에 필요한 처리를 수행합니다.
    protected RefreshToken() {
    }

    // 함수 역할: RefreshToken에서 인증 토큰 생성, 검증, 재발급에 필요한 처리를 수행합니다.
    public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    // 함수 역할: onCreate에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 함수 역할: getUser에서 필요한 값이나 상태를 조회해 반환합니다.
    public User getUser() {
        return user;
    }

    // 함수 역할: isUsable에서 필요한 값이나 상태를 조회해 반환합니다.
    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(LocalDateTime.now()) && user.isEnabled();
    }

    // 함수 역할: revoke에서 더 이상 사용하지 않는 데이터나 권한 정보를 정리합니다.
    public void revoke() {
        this.revoked = true;
    }
}
