package com.hanium.presentation.application.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineResponse;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TextFeedbackPromptBuilder {
    private static final int MAX_TRANSCRIPT_CHARS = 6000;

    private final ObjectMapper objectMapper;

    public TextFeedbackPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(AnalysisEngineResponse basic, VideoLlmEngineResponse videoLlm) throws JsonProcessingException {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("transcript", truncateTranscript(basic.transcript()));
        context.put("durationSeconds", basic.durationSeconds());
        context.put("speechAndVisionMetrics", nullToEmptyMap(basic.metrics()));
        context.put("scores", nullToEmptyList(basic.scores()));
        context.put("totalScore", basic.totalScore());
        context.put("scoreSummary", nullToEmptyMap(basic.summary()));
        context.put("analysisObservations", nullToEmptyList(basic.observations()));
        context.put("videoLlmProvider", videoLlm.provider());
        context.put("videoLlmMetadata", nullToEmptyMap(videoLlm.metadata()));
        context.put("videoLlmObservations", nullToEmptyList(videoLlm.observations()));

        String contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);

        return """
                당신은 발표 코칭 전문가입니다. 아래 분석 데이터를 바탕으로 한국어 최종 피드백을 작성하세요.

                규칙:
                - 반드시 JSON 객체만 반환하세요.
                - Markdown 코드블록, 설명 문장, <think> 태그를 반환하지 마세요.
                - 입력된 점수는 새로 계산하거나 변경하지 마세요.
                - 실제 입력 데이터에 근거한 설명, 개선 방법, 예상 질문만 생성하세요.
                - 과장된 표현을 피하고 발표자가 바로 실천할 수 있게 작성하세요.
                - 관련 지표명은 가능한 한 입력 데이터의 category 또는 metric 이름을 사용하세요.

                반환 JSON 스키마:
                {
                  "presentationSummary": "발표 전체 요약",
                  "overallFeedback": "종합 피드백",
                  "strengths": [
                    {
                      "title": "강점 제목",
                      "description": "설명",
                      "relatedMetric": "eyeContact"
                    }
                  ],
                  "weaknesses": [
                    {
                      "title": "개선 필요 항목",
                      "description": "설명",
                      "relatedMetric": "speechSpeed"
                    }
                  ],
                  "improvementActions": [
                    {
                      "title": "개선 방법",
                      "description": "실천 방법"
                    }
                  ],
                  "expectedQuestions": [
                    {
                      "question": "예상 질문",
                      "intent": "질문 의도",
                      "suggestedAnswer": "답변 방향"
                    }
                  ]
                }

                입력 데이터:
                %s
                """.formatted(contextJson);
    }

    private String truncateTranscript(String transcript) {
        String source = transcript == null ? "" : transcript.trim();
        if (source.length() <= MAX_TRANSCRIPT_CHARS) {
            return source;
        }

        int chunk = MAX_TRANSCRIPT_CHARS / 2;
        String head = source.substring(0, chunk);
        String tail = source.substring(source.length() - chunk);
        return head + "\n...[중간 대본 생략]...\n" + tail;
    }

    private Map<String, Object> nullToEmptyMap(Map<String, Object> source) {
        return source == null ? Map.of() : source;
    }

    private List<Map<String, Object>> nullToEmptyList(List<Map<String, Object>> source) {
        return source == null ? List.of() : source;
    }
}
