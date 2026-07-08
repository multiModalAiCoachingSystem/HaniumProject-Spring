// 역할: Spring Boot 백엔드 애플리케이션을 시작하는 진입점입니다.
package com.hanium.presentation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PresentationApplication {
    // 함수 역할: 애플리케이션 실행 진입점을 시작합니다.
    public static void main(String[] args) {
        SpringApplication.run(PresentationApplication.class, args);
    }
}
