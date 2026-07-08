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
- 연결된 video-llm-engine 서비스: `VideoLlmAnalyzer.analyze`, `VideoSampler.prepare_samples`, `QwenVlRunner.analyze_frames`
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
  "provider": "OLLAMA",
  "model": "qwen3-vl:4b",
  "metadata": {
    "durationSeconds": 180.0,
    "width": 1920,
    "height": 1080,
    "fps": 30.0,
    "frameCount": 5400,
    "videoCodec": "h264",
    "audioCodec": "aac",
    "formatName": "mov,mp4,m4a,3gp,3g2,mj2",
    "sizeBytes": 10485760
  },
  "sampledFrames": [
    {
      "index": 0,
      "timestampSeconds": 0.0,
      "path": "/data/video-llm/job-20/frames/frame_001.jpg"
    }
  ],
  "audio": {
    "path": "/data/video-llm/job-20/audio/speech.wav",
    "sampleRate": 16000,
    "channels": 1
  },
  "observations": [
    {
      "id": 1,
      "type": "INFO",
      "category": "VIDEO_QUALITY",
      "startSeconds": 0.0,
      "endSeconds": 12.0,
      "title": "Qwen-VL 분석 결과",
      "description": "관찰 내용",
      "suggestion": ""
    }
  ]
}
```

오류 상태 코드:

- 422: `FFmpegError`. 영상 파일 없음, ffmpeg/ffprobe 처리 실패 등
- 422: Pydantic 검증 실패
- 500: 처리 중 예기치 못한 오류

## 모델 호출

`VIDEO_LLM_PROVIDER=OLLAMA`이면 `QwenVlRunner`가 Ollama `/api/generate`를 호출한다.

- base URL: `OLLAMA_BASE_URL`
- 모델: `VIDEO_LLM_MODEL`
- 기본값 위치: `video-llm-engine/app/model/model_config.py`
- 요청 옵션: `stream=false`, `format=json`, `images`는 최대 4개 샘플 프레임

`VIDEO_LLM_PROVIDER`가 `OLLAMA`가 아니면 `_mock_observations()`가 mock 관찰 결과를 반환한다.
