// 역할: 외부 분석 엔진 HTTP API를 호출하는 인프라 클라이언트입니다.
package com.hanium.presentation.infrastructure.client.analysis;

import com.hanium.presentation.global.properties.AnalysisEngineProperties;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineRequest;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@SuppressWarnings("null")
public class AnalysisEngineClient {
    private final WebClient webClient;
    private final AnalysisEngineProperties properties;

    // 함수 역할: AnalysisEngineClient에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    public AnalysisEngineClient(WebClient webClient, AnalysisEngineProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    // 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    public AnalysisEngineResponse analyze(AnalysisEngineRequest request) {
        return webClient.post()
                .uri(properties.getBaseUrl() + "/api/basic-analysis")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AnalysisEngineResponse.class)
                .block(Duration.ofMinutes(10));
    }
}
