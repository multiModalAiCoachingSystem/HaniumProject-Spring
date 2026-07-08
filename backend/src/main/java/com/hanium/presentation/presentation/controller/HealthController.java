// 역할: HTTP 요청을 받아 애플리케이션 서비스로 전달하고 API 응답을 구성하는 컨트롤러입니다.
package com.hanium.presentation.presentation.controller;

import com.hanium.presentation.global.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    // 함수 역할: health에서 서비스 상태 확인 요청에 대한 응답을 반환합니다.
    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"), "정상 동작 중입니다.");
    }
}
