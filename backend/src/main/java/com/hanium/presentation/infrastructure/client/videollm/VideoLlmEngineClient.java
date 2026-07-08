// 역할: 외부 분석 엔진 HTTP API를 호출하는 인프라 클라이언트입니다.
package com.hanium.presentation.infrastructure.client.videollm;

import com.hanium.presentation.global.properties.VideoLlmEngineProperties;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineRequest;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@SuppressWarnings("null")
public class VideoLlmEngineClient {
    private final WebClient webClient;
    private final VideoLlmEngineProperties properties;

    // 함수 역할: VideoLlmEngineClient에서 영상 파일 정보, 변환, 분석 요청에 필요한 처리를 수행합니다.
    public VideoLlmEngineClient(WebClient webClient, VideoLlmEngineProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    // 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    public VideoLlmEngineResponse analyze(VideoLlmEngineRequest request) {
        return webClient.post()
                .uri(properties.getBaseUrl() + "/api/video-llm-analysis")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VideoLlmEngineResponse.class)
                .block(Duration.ofMinutes(10));
    }
}
