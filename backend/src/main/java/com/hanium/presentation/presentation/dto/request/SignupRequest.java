// 역할: 클라이언트 요청 바디를 검증 가능한 객체로 전달하는 요청 DTO입니다.
package com.hanium.presentation.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일을 입력해주세요.")
        String email,

        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해주세요.")
        String passwordConfirm,

        @NotBlank(message = "이름 또는 닉네임을 입력해주세요.")
        String nickname
) {
}
