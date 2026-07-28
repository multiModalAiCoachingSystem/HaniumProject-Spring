# Video LLM Engine API

FastAPI 앱은 `video-llm-engine/app/main.py`에서 생성된다. 백엔드 `VideoLlmEngineClient`는 `POST {VIDEO_LLM_ENGINE_BASE_URL}/api/video-llm-analysis`를 호출한다.

## GET `/health`

- HTTP Method: GET
- URL: `/health`
- 인증 필요 여부: 없음
- 요청 파라미터: 없음
- 연결 코드: `video-llm-engine/app/api/health.py`

성공 응답:

```json
{
  "status": "UP",
  "ffmpeg": "UP"
}
```

ffmpeg/ffprobe가 없으면 `ffmpeg` 필드에 `DOWN: ...` 메시지가 들어가지만 HTTP 상태는 200이다.

## POST `/api/video-llm-analysis`

- HTTP Method: POST
- URL: `/api/video-llm-analysis`
- 인증 필요 여부: 없음. 내부 네트워크 호출을 전제로 한다.
- 연결된 백엔드: `AnalysisPipelineService.runAnalysis` -> `VideoLlmEngineClient.analyze`
- 연결된 video-llm-engine 서비스: `VideoLlmAnalyzer.analyze`, `VideoSampler.prepare_samples`, `NvidiaNemotronRunner.analyze_frames`, `QwenVlRunner.analyze_frames`
- 소유권 검증 여부: video-llm-engine 자체에는 없음. 백엔드가 분석 작업 생성 전에 검증한다.

요청 JSON:

```json
{
  "jobId": 20,
  "videoId": 10,
  "filePath": "uploads/sample.mp4"
}
```

성공 응답 예시:

```json
{
  "jobId": 20,
  "videoId": 10,
  "provider": "NVIDIA_NIM",
  "model": "nvidia/nemotron-nano-12b-v2-vl",
  "metadata": {
    "durationSeconds": 180.0,
    "width": 1920,
    "height": 1080,
    "fps": 30.0,
    "frameCount": 5400,
    "videoCodec": "h264",
    "audioCodec": "aac",
    "formatName": "mov,mp4,m4a,3gp,3g2,mj2",
    "sizeBytes": 10485760,
    "fallbackUsed": false,
    "videoLlmResult": {
      "provider": "NVIDIA_NIM",
      "model": "nvidia/nemotron-nano-12b-v2-vl",
      "summary": "Factual visual summary in English",
      "visualMetrics": {},
      "fallbackUsed": false
    }
  },
  "sampledFrames": [
    {
      "index": 0,
      "timestampSeconds": 0.0,
      "path": "/app/storage/video-llm/job-20/frames/frame_001.jpg"
    }
  ],
  "audio": {
    "path": "/app/storage/video-llm/job-20/audio/speech.wav",
    "sampleRate": 16000,
    "channels": 1
  },
  "observations": [
    {
      "id": 1,
      "type": "STRENGTH",
      "category": "GESTURE",
      "startSeconds": 12.5,
      "endSeconds": 22.5,
      "title": "GESTURE visual strength",
      "description": "Uses both hands naturally while explaining a key point.",
      "suggestion": "Keep this visual behavior consistent in future presentations.",
      "confidence": 0.82
    }
  ]
}
```

오류 상태 코드:

- 422: `FFmpegError`. 영상 파일 없음, ffmpeg/ffprobe 처리 실패 등
- 422: Pydantic 검증 실패
- 500: 처리 중 예기치 못한 오류

NVIDIA/Ollama provider 호출 실패는 가능한 한 HTTP 500으로 전파하지 않고 `observations` fallback으로 처리한다.

## 모델 호출

`VIDEO_LLM_PROVIDER=NVIDIA_NIM`이면 `NvidiaNemotronRunner`가 NVIDIA OpenAI-compatible Chat Completions API를 호출한다.

- endpoint: `NVIDIA_API_BASE_URL`
- 모델: `VIDEO_LLM_MODEL`
- 인증: `Authorization: Bearer ${NVIDIA_API_KEY}`
- 입력: sampler는 최대 16개 프레임으로 제한한다. NVIDIA에는 `NVIDIA_MAX_IMAGES` 기본 8개만 전달하며 코드 상한은 10개다.
- 원본 대용량 영상 직접 `video_url` 전송: 미구현

`VIDEO_LLM_PROVIDER=OLLAMA`이면 `QwenVlRunner`가 Ollama `/api/generate`를 호출한다.

- base URL: `OLLAMA_BASE_URL`
- 모델: `OLLAMA_VIDEO_LLM_MODEL`
- 요청 옵션: `stream=false`, `format=json`, `images`는 최대 4개 샘플 프레임

`VIDEO_LLM_PROVIDER=NONE`이면 외부 모델 호출 없이 결정적 fallback observation을 반환한다.
