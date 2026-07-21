// 역할: 클라이언트에 반환할 API 응답 데이터를 담는 응답 DTO입니다.
package com.hanium.presentation.presentation.dto.response;

import com.hanium.presentation.common.util.ApiDateTimes;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import java.time.OffsetDateTime;

public record SignupResponse(
        Long userId,
        String email,
        String nickname,
        UserRole role,
        OffsetDateTime createdAt
) {
    // 함수 역할: from에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                ApiDateTimes.inServiceZone(user.getCreatedAt())
        );
    }
}
