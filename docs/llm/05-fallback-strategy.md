# Fallback 전략

## Video LLM fallback

`QwenVlRunner.analyze_frames()`는 다음 상황에서 fallback observation을 반환한다.

- 샘플 프레임 경로가 없음: 빈 리스트 반환
- Ollama 요청 실패: `Qwen-VL request failed: ...` 메시지를 담은 `INFO` observation 반환
- Ollama 응답 JSON 파싱 실패: raw 응답 앞부분을 `description`에 담은 `INFO` observation 반환

fallback observation 예시:

```json
{
  "type": "INFO",
  "category": "VIDEO_QUALITY",
  "startSeconds": 0.0,
  "endSeconds": 12.0,
  "title": "Qwen-VL 분석 결과",
  "description": "fallback message",
  "suggestion": ""
}
```

## provider fallback

`VIDEO_LLM_PROVIDER`가 `OLLAMA`가 아니면 `VideoLlmAnalyzer._mock_observations()`가 mock 관찰 결과를 반환한다. docker-compose는 `VIDEO_LLM_PROVIDER=OLLAMA`로 설정한다.

## MediaPipe fallback

`analysis-engine/app/services/vision_metrics.py`는 다음 상황에서 fallback 지표를 반환한다.

- 영상 파일 없음
- OpenCV/MediaPipe import 실패
- 영상 열기 실패
- frame count 없음
- 탐지 가능한 frame 없음

fallback에는 `visionAnalysisFallback=True`와 `visionAnalysisFallbackReason`이 포함된다.

## STT fallback

`SttService`는 provider가 `OFF`, `NONE`, `DISABLED`, `MOCK`이면 unavailable 결과를 반환한다. faster-whisper import 실패, ffmpeg 없음, MediaInfo 오류, 런타임 오류도 `available=false`와 `error`로 반환하고 전체 분석은 계속된다.

## Text LLM fallback

`TextFeedbackService`는 Text LLM 실패를 pipeline 실패로 전파하지 않는다. 다음 상황에서는 fallback 피드백을 생성해 `summary_json.textFeedback`에 저장한다.

- `TEXT_LLM_PROVIDER=NONE`
- `TEXT_LLM_PROVIDER=OPENAI`: 현재 실제 OpenAI 호출은 미구현
- 지원하지 않는 provider 값
- Ollama 서버 미실행
- `qwen3:4b` 모델 미설치 또는 Ollama 오류
- 응답 timeout
- 빈 응답
- JSON 파싱 실패
- 프롬프트 생성 실패

fallback 피드백도 `presentationSummary`, `overallFeedback`, `strengths`, `weaknesses`, `improvementActions`, `expectedQuestions` 구조를 유지한다. `summary_json.textLlm.fallback=true`와 안전한 `fallbackReason`이 함께 저장된다.

## 백엔드 실패 fallback

엔진 호출 예외, 저장 실패 등 pipeline 수준 예외는 `AnalysisJob`을 `FAILED`로 변경한다. 사용자에게는 `AnalysisFailureMessages`가 만든 안전한 실패 메시지만 노출한다.
