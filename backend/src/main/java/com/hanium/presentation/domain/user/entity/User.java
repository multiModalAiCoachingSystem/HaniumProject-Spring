// 역할: 데이터베이스 테이블과 매핑되는 핵심 도메인 엔티티입니다.
package com.hanium.presentation.domain.user.entity;

import com.hanium.presentation.domain.user.type.UserRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 80)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 함수 역할: User에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    protected User() {
    }

    // 함수 역할: User에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private User(String email, String password, String nickname, UserRole role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.enabled = true;
    }

    // 함수 역할: createUser에서 새 데이터나 처리 결과를 생성해 저장합니다.
    public static User createUser(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, UserRole.USER);
    }

    // 함수 역할: onCreate에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 함수 역할: onUpdate에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 함수 역할: getId에서 필요한 값이나 상태를 조회해 반환합니다.
    public Long getId() {
        return id;
    }

    // 함수 역할: getEmail에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getEmail() {
        return email;
    }

    // 함수 역할: getPassword에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getPassword() {
        return password;
    }

    // 함수 역할: getNickname에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getNickname() {
        return nickname;
    }

    // 함수 역할: getRole에서 필요한 값이나 상태를 조회해 반환합니다.
    public UserRole getRole() {
        return role;
    }

    // 함수 역할: isEnabled에서 필요한 값이나 상태를 조회해 반환합니다.
    public boolean isEnabled() {
        return enabled;
    }

    // 함수 역할: 관리자 추방 요청에 맞춰 사용자 계정을 비활성화합니다.
    public void disable() {
        this.enabled = false;
    }

    // 함수 역할: 운영 콘솔의 권한 변경 요청에 맞춰 사용자 권한을 갱신합니다.
    public void changeRole(UserRole role) {
        this.role = role;
    }

    // 함수 역할: getCreatedAt에서 필요한 값이나 상태를 조회해 반환합니다.
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
