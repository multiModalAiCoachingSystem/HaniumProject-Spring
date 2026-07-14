# JSON 출력 스키마

## analysis-engine 응답

`analysis-engine/app/schemas/basic_analysis_response.py`와 실제 `BasicAnalyzer.analyze()` 반환 구조 기준이다.

```json
{
  "jobId": 20,
  "videoId": 10,
  "durationSeconds": 180.0,
  "videoInfo": {},
  "transcript": "발표 텍스트",
  "stt": {},
  "metrics": {},
  "scores": [],
  "totalScore": 82.5,
  "summary": {},
  "observations": []
}
```

### `scores[]`

```json
{
  "category": "POSTURE",
  "name": "자세",
  "score": 82.5,
  "weight": 0.25,
  "grade": "양호",
  "summary": "어깨 수평, 고개 움직임, 상체 안정성을 기준으로 계산했습니다.",
  "metrics": {}
}
```

### `observations[]`

```json
{
  "id": 1,
  "type": "STRENGTH",
  "category": "POSTURE",
  "startSeconds": 0.0,
  "endSeconds": 12.0,
  "title": "자세 강점",
  "description": "전체적으로 안정적인 모습입니다.",
  "suggestion": "현재 자세 흐름은 유지하세요."
}
```

## video-llm-engine 응답

`video-llm-engine/app/schemas/video_llm_response.py`와 실제 `VideoLlmAnalyzer.analyze()` 반환 구조 기준이다.

```json
{
  "jobId": 20,
  "videoId": 10,
  "provider": "OLLAMA",
  "model": "qwen3-vl:4b",
  "metadata": {},
  "sampledFrames": [],
  "audio": {},
  "observations": []
}
```

스키마 파일의 `VideoLlmResponse`에는 `model` 필드가 없지만 실제 응답 dict에는 `model`이 포함된다. 백엔드 `VideoLlmEngineResponse`는 `@JsonIgnoreProperties(ignoreUnknown = true)`를 사용하므로 이 필드를 무시한다.

## Qwen-VL 프롬프트의 JSON 요구 형식

`QwenVlRunner._prompt()`는 모델에 다음 형태를 요구한다.

```json
{
  "observations": [
    {
      "type": "STRENGTH or WEAKNESS or INFO",
      "category": "POSTURE or EYE_CONTACT or EXPRESSION or GESTURE or VIDEO_QUALITY",
      "startSeconds": 0,
      "endSeconds": 12,
      "title": "short Korean title",
      "description": "specific Korean visual observation",
      "suggestion": "specific Korean coaching suggestion"
    }
  ]
}
```

## 백엔드 저장 스키마

백엔드는 최종 결과를 다음 JSON 문자열로 저장한다.

- `scores_json`: `scores[]`
- `feedback_json`: analysis-engine observations + video-llm-engine observations
- `summary_json`: transcript, stt, metrics, videoInfo, videoLlmProvider, videoLlmMetadata, sampledFrames, extractedAudio, textFeedback, textLlm

### `summary_json.textFeedback`

`TextFeedbackService`가 생성하거나 fallback으로 채우는 최종 한국어 피드백이다.

```json
{
  "presentationSummary": "발표 전체 요약",
  "overallFeedback": "종합 피드백",
  "strengths": [
    {
      "title": "강점 제목",
      "description": "설명",
      "relatedMetric": "EYE_CONTACT"
    }
  ],
  "weaknesses": [
    {
      "title": "개선 필요 항목",
      "description": "설명",
      "relatedMetric": "VOICE"
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
```

### `summary_json.textLlm`

```json
{
  "provider": "OLLAMA",
  "model": "qwen3:4b",
  "fallback": false,
  "fallbackReason": "text_llm_generation_failed"
}
```

`fallbackReason`은 fallback이 발생한 경우에만 포함된다.
