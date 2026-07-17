package com.hanium.presentation.application.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.global.properties.TextLlmProperties;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineResponse;
import com.hanium.presentation.infrastructure.client.textllm.OllamaTextLlmClient;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TextFeedbackServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TextLlmProperties properties = new TextLlmProperties();
    private final OllamaTextLlmClient ollamaClient = Mockito.mock(OllamaTextLlmClient.class);
    private final TextFeedbackPromptBuilder promptBuilder = new TextFeedbackPromptBuilder(objectMapper);
    private final TextFeedbackService service = new TextFeedbackService(
            properties,
            ollamaClient,
            promptBuilder,
            objectMapper
    );

    @Test
    void noneProviderReturnsFallbackWithoutCallingOllama() {
        properties.setProvider("NONE");
        properties.setModel("qwen3:4b");

        TextFeedbackResult result = service.generate(1L, basicResponse(), videoLlmResponse());

        assertThat(result.fallback()).isTrue();
        assertThat(result.payload()).containsKeys(
                "presentationSummary",
                "overallFeedback",
                "strengths",
                "weaknesses",
                "improvementActions",
                "expectedQuestions"
        );
        verify(ollamaClient, never()).generate(anyString());
    }

    @Test
    void ollamaProviderParsesJsonFeedback() {
        properties.setProvider("OLLAMA");
        properties.setModel("qwen3:4b");
        when(ollamaClient.generate(anyString())).thenReturn("""
                {
                  "presentationSummary": "발표 주제가 명확합니다.",
                  "overallFeedback": "시선과 말하기 속도를 유지하세요.",
                  "strengths": [
                    {
                      "title": "시선 유지",
                      "description": "카메라를 자주 바라봅니다.",
                      "relatedMetric": "EYE_CONTACT"
                    }
                  ],
                  "weaknesses": [
                    {
                      "title": "말하기 속도",
                      "description": "일부 구간이 빠릅니다.",
                      "relatedMetric": "VOICE"
                    }
                  ],
                  "improvementActions": [
                    {
                      "title": "속도 조절",
                      "description": "핵심 문장 앞에서 잠시 멈추세요."
                    }
                  ],
                  "expectedQuestions": [
                    {
                      "question": "핵심 근거는 무엇인가요?",
                      "intent": "논리 구조 확인",
                      "suggestedAnswer": "근거 두 가지를 먼저 말하세요."
                    }
                  ]
                }
                """);

        TextFeedbackResult result = service.generate(1L, basicResponse(), videoLlmResponse());

        assertThat(result.fallback()).isFalse();
        assertThat(result.payload().get("presentationSummary")).isEqualTo("발표 주제가 명확합니다.");
        assertThat(result.metadata()).containsEntry("provider", "OLLAMA")
                .containsEntry("model", "qwen3:4b")
                .containsEntry("fallback", false);
    }

    @Test
    void invalidJsonFallsBackWithoutThrowing() {
        properties.setProvider("OLLAMA");
        properties.setModel("qwen3:4b");
        when(ollamaClient.generate(anyString())).thenReturn("JSON이 아닌 응답");

        TextFeedbackResult result = service.generate(1L, basicResponse(), videoLlmResponse());

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo("text_llm_generation_failed");
        assertThat(result.payload().get("overallFeedback")).isNotNull();
    }

    private AnalysisEngineResponse basicResponse() {
        return new AnalysisEngineResponse(
                1L,
                10L,
                120.0,
                Map.of(),
                "오늘 발표에서는 서비스의 핵심 가치와 구현 결과를 설명하겠습니다.",
                Map.of(),
                Map.of("speechSpeedSpm", 310.0, "fillerWordRatio", 0.01),
                List.of(
                        score("EYE_CONTACT", "시선", 88.0),
                        score("VOICE", "음성", 72.0)
                ),
                80.0,
                Map.of("priority", "VOICE"),
                List.of()
        );
    }

    private Map<String, Object> score(String category, String name, double score) {
        return Map.of(
                "category", category,
                "name", name,
                "score", score,
                "weight", 0.2,
                "grade", "보통",
                "summary", "요약",
                "metrics", Map.of()
        );
    }

    private VideoLlmEngineResponse videoLlmResponse() {
        return new VideoLlmEngineResponse(1L, 10L, "MOCK", "mock-model", Map.of(), List.of(), Map.of(), List.of());
    }
}
