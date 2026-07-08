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

Text LLM 호출 자체가 현재 미구현이므로 별도의 모델 실패 fallback도 미구현이다. 현재 최종 결과는 analysis-engine과 video-llm-engine의 observation 병합으로 구성된다.

## 백엔드 실패 fallback

엔진 호출 예외, 저장 실패 등 pipeline 수준 예외는 `AnalysisJob`을 `FAILED`로 변경한다. 사용자에게는 `AnalysisFailureMessages`가 만든 안전한 실패 메시지만 노출한다.
