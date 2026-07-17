# Fallback 전략

## Video LLM fallback

`VIDEO_LLM_PROVIDER=NVIDIA_NIM`인 경우 현재 fallback 흐름은 다음과 같다.

1. NVIDIA NIM `nvidia/nemotron-nano-12b-v2-vl` 호출
2. 실패 시 `VIDEO_LLM_FALLBACK_PROVIDER=OLLAMA`이면 Ollama `qwen3-vl:4b` 호출
3. Ollama도 사용할 수 없거나 provider가 `NONE`이면 결정적 fallback observation 반환

fallback 가능한 상황:

- `NVIDIA_API_KEY` 없음
- HTTP 400, 401, 403, 413, 429, 5xx 오류
- timeout
- 네트워크 오류
- 빈 응답
- JSON 파싱 실패
- payload 크기 제한 초과

API Key, Authorization header, base64 payload는 로그에 남기지 않는다.

## MediaPipe fallback

`analysis-engine/app/services/vision_metrics.py`는 다음 상황에서 vision fallback metric을 반환한다.

- 영상 파일 없음
- OpenCV 또는 MediaPipe import 실패
- 영상 열기 실패
- frame count 없음
- 감지 가능한 frame 없음
- MediaPipe 실행 중 예외

fallback 결과에는 `visionAnalysisFallback=true`와 `visionAnalysisFallbackReason`이 포함된다.

## Expression fallback

표정 분석은 MediaPipe FaceMesh landmark와 crop retry를 사용한다. 영상 파일 누락, MediaPipe/OpenCV 오류, 프레임 읽기 실패, 감지 가능한 얼굴 프레임 부재 등에서는 다음 fallback metric이 사용된다.

```text
naturalExpressionRatio = 0.74
tenseExpressionRatio = 0.12 또는 긴 발표에서 0.16
smileRatio = 0.18
faceDetectionRatio = 0.0
visionAnalysisFallback = true
```

`ScoreCalculator.expression_score()`는 fallback 또는 `faceDetectionRatio <= 0.0`인 경우 표정 점수 최고점을 66점으로 제한한다. 따라서 fallback 결과가 실제 landmark 분석보다 유리하게 95~100점으로 포화되지 않는다.

## STT fallback

`SttService`는 provider가 `OFF`, `NONE`, `DISABLED`, `MOCK`이면 실제 STT 없이 unavailable 결과를 반환한다. faster-whisper import 실패, ffmpeg 없음, MediaInfo 오류, 오디오 처리 오류도 `available=false`와 안전한 `error` 값으로 반환하고 전체 분석은 계속 진행한다.

## Text LLM fallback

`TextFeedbackService`는 Text LLM 실패를 pipeline 실패로 전파하지 않는다. 다음 상황에서는 fallback 피드백을 생성해 `summary_json.textFeedback`에 저장한다.

- `TEXT_LLM_PROVIDER=NONE`
- `TEXT_LLM_PROVIDER=OPENAI`: 현재 실제 OpenAI 호출 미구현
- 지원하지 않는 provider 값
- Ollama 서버 미실행
- `qwen3:4b` 모델 미설치 또는 Ollama 오류
- 응답 timeout
- 빈 응답
- JSON 파싱 실패
- 프롬프트 생성 실패

fallback 피드백도 `presentationSummary`, `overallFeedback`, `strengths`, `weaknesses`, `improvementActions`, `expectedQuestions` 구조를 유지한다. `summary_json.textLlm.fallback=true`와 안전한 `fallbackReason`이 함께 저장된다.

## Backend failure fallback

엔진 호출 예외, 결과 저장 실패 등 pipeline 자체를 계속할 수 없는 오류는 `AnalysisJob`을 `FAILED`로 변경한다. 사용자 응답에는 `AnalysisFailureMessages`가 만든 안전한 실패 메시지만 노출한다.
