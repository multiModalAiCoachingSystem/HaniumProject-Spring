// 역할: 클라이언트에 반환할 API 응답 데이터를 담는 응답 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

import com.hanium.presentation.domain.user.entity.User;

public record UserMeResponse(Long userId, String email, String nickname, String role) {
    // 함수 역할: from에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole().name());
    }
}
