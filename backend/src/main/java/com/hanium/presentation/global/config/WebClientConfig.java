// 역할: 백엔드의 WebClientConfig 기능을 담당하는 Java 코드입니다.
package com.hanium.presentation.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    // 함수 역할: webClient에서 외부 API 또는 백엔드 API 호출을 수행합니다.
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
