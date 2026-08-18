package com.hanium.presentation.application.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.global.properties.TextLlmProperties;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineResponse;
import com.hanium.presentation.infrastructure.client.textllm.OllamaTextLlmClient;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TextFeedbackService {
    private static final Logger log = LoggerFactory.getLogger(TextFeedbackService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final TextLlmProperties properties;
    private final OllamaTextLlmClient ollamaClient;
    private final TextFeedbackPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public TextFeedbackService(TextLlmProperties properties,
                               OllamaTextLlmClient ollamaClient,
                               TextFeedbackPromptBuilder promptBuilder,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.ollamaClient = ollamaClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public TextFeedbackResult generate(Long jobId, AnalysisEngineResponse basic, VideoLlmEngineResponse videoLlm) {
        String provider = normalizeProvider(properties.getProvider());
        String model = safeModel();

        if ("NONE".equals(provider)) {
            return fallback(basic, provider, model, "text_llm_disabled");
        }
        if ("OPENAI".equals(provider)) {
            log.warn("[FEEDBACK] OPENAI Provider가 미구현되어 fallback을 사용합니다. jobId={}", jobId);
            return fallback(basic, provider, model, "openai_provider_not_implemented");
        }
        if (!"OLLAMA".equals(provider)) {
            log.warn("[FEEDBACK] 지원하지 않는 Provider라 fallback을 사용합니다. jobId={} provider={}", jobId, provider);
            return fallback(basic, provider, model, "unsupported_provider");
        }

        try {
            log.info("[FEEDBACK] 한국어 피드백을 생성하고 있습니다. jobId={} provider={} model={}",
                    jobId, provider, model);
            String prompt = promptBuilder.build(basic, videoLlm);
            String rawResponse = ollamaClient.generate(prompt);
            Map<String, Object> parsed = parseJsonObject(rawResponse);
            Map<String, Object> normalized = normalizePayload(parsed, fallbackPayload(basic));
            log.info("[FEEDBACK] 한국어 피드백을 생성했습니다. jobId={} provider={} model={} fallback=false",
                    jobId, provider, model);
            return TextFeedbackResult.success(normalized, provider, model);
        } catch (Exception exception) {
            log.warn("[FEEDBACK] 피드백 생성 fallback을 사용합니다. jobId={} provider={} model={} reason={}",
                    jobId, provider, model, exception.getClass().getSimpleName());
            return fallback(basic, provider, model, "text_llm_generation_failed");
        }
    }

    public TextFeedbackResult fallback(AnalysisEngineResponse basic, String provider, String model, String reason) {
        return TextFeedbackResult.fallback(fallbackPayload(basic), provider, model, reason);
    }

    private Map<String, Object> parseJsonObject(String rawResponse) throws JsonProcessingException {
        String json = extractJsonObject(rawResponse);
        return objectMapper.readValue(json, MAP_TYPE);
    }

    private String extractJsonObject(String rawResponse) {
        String source = rawResponse == null ? "" : rawResponse.trim();
        if (source.startsWith("```")) {
            source = source.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }

        int start = source.indexOf('{');
        int end = source.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Text LLM response does not contain a JSON object");
        }
        return source.substring(start, end + 1);
    }

    private Map<String, Object> normalizePayload(Map<String, Object> parsed, Map<String, Object> fallback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("presentationSummary", safeString(parsed.get("presentationSummary"),
                fallback.get("presentationSummary")));
        payload.put("overallFeedback", safeString(parsed.get("overallFeedback"),
                fallback.get("overallFeedback")));
        payload.put("strengths", normalizeList(parsed.get("strengths"),
                listValue(fallback.get("strengths")), "title", "description", "relatedMetric"));
        payload.put("weaknesses", normalizeList(parsed.get("weaknesses"),
                listValue(fallback.get("weaknesses")), "title", "description", "relatedMetric"));
        payload.put("improvementActions", normalizeList(parsed.get("improvementActions"),
                listValue(fallback.get("improvementActions")), "title", "description"));
        payload.put("expectedQuestions", normalizeList(parsed.get("expectedQuestions"),
                listValue(fallback.get("expectedQuestions")), "question", "intent", "suggestedAnswer"));
        return payload;
    }

    private List<Map<String, Object>> normalizeList(Object value, List<Map<String, Object>> fallback, String... fields) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return fallback;
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String field : fields) {
                entry.put(field, safeString(map.get(field), ""));
            }
            if (entry.values().stream().anyMatch(text -> !text.toString().isBlank())) {
                normalized.add(entry);
            }
        }
        return normalized.isEmpty() ? fallback : normalized;
    }

    private Map<String, Object> fallbackPayload(AnalysisEngineResponse basic) {
        List<Map<String, Object>> scores = nullToEmptyList(basic.scores());
        Map<String, Object> strongest = strongestScore(scores);
        Map<String, Object> weakest = weakestScore(scores);
        double totalScore = basic.totalScore() == null ? 0.0 : basic.totalScore();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("presentationSummary", "총점 " + roundOne(totalScore)
                + "점을 기준으로 생성한 발표 요약입니다. 자동 피드백 생성이 제한되어 기본 분석 결과를 사용했습니다.");
        payload.put("overallFeedback", "점수 계산 결과와 항목별 관찰 내용을 기준으로 강점은 유지하고 낮은 점수 항목부터 개선하는 것이 좋습니다.");
        payload.put("strengths", List.of(Map.of(
                "title", scoreName(strongest) + " 강점",
                "description", scoreName(strongest) + " 항목이 상대적으로 안정적입니다. 현재의 발표 흐름을 유지하세요.",
                "relatedMetric", scoreMetric(strongest)
        )));
        payload.put("weaknesses", List.of(Map.of(
                "title", scoreName(weakest) + " 개선 필요",
                "description", scoreName(weakest) + " 항목을 먼저 보완하면 전체 발표 완성도를 올릴 수 있습니다.",
                "relatedMetric", scoreMetric(weakest)
        )));
        payload.put("improvementActions", List.of(
                Map.of(
                        "title", "가장 낮은 항목을 기준으로 1분 재녹화",
                        "description", scoreName(weakest) + " 항목만 의식해서 짧게 다시 녹화하고 기존 영상과 비교하세요."
                ),
                Map.of(
                        "title", "핵심 문장 앞뒤로 멈춤 만들기",
                        "description", "중요한 문장 앞뒤에 짧은 멈춤을 넣어 속도, 시선, 제스처를 함께 정리하세요."
                )
        ));
        payload.put("expectedQuestions", List.of(Map.of(
                "question", "발표의 핵심 주장과 근거는 무엇인가요?",
                "intent", "발표자가 주요 메시지를 명확하게 구조화했는지 확인하기 위한 질문입니다.",
                "suggestedAnswer", "핵심 주장 한 문장, 근거 두 가지, 기대 효과 순서로 답변을 준비하세요."
        )));
        return payload;
    }

    private Map<String, Object> strongestScore(List<Map<String, Object>> scores) {
        return scores.stream()
                .filter(score -> score.get("score") instanceof Number)
                .max((left, right) -> Double.compare(scoreValue(left), scoreValue(right)))
                .orElse(Map.of("name", "종합", "category", "OVERALL", "score", 0.0));
    }

    private Map<String, Object> weakestScore(List<Map<String, Object>> scores) {
        return scores.stream()
                .filter(score -> score.get("score") instanceof Number)
                .min((left, right) -> Double.compare(scoreValue(left), scoreValue(right)))
                .orElse(Map.of("name", "종합", "category", "OVERALL", "score", 0.0));
    }

    private double scoreValue(Map<String, Object> score) {
        return ((Number) score.get("score")).doubleValue();
    }

    private String scoreName(Map<String, Object> score) {
        return safeString(score.get("name"), "종합");
    }

    private String scoreMetric(Map<String, Object> score) {
        return safeString(score.get("category"), "OVERALL");
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "NONE";
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    private String safeModel() {
        return properties.getModel() == null ? "" : properties.getModel().trim();
    }

    private String safeString(Object value, Object fallback) {
        String text = value == null ? "" : value.toString().trim();
        if (!text.isBlank()) {
            return text;
        }
        return fallback == null ? "" : fallback.toString();
    }

    private String roundOne(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue(Object value) {
        return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
    }

    private List<Map<String, Object>> nullToEmptyList(List<Map<String, Object>> source) {
        return source == null ? List.of() : source;
    }
}
