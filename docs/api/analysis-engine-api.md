# Analysis Engine API

FastAPI 앱은 `analysis-engine/app/main.py`에서 생성된다. 백엔드 `AnalysisEngineClient`는 `POST {ANALYSIS_ENGINE_BASE_URL}/api/basic-analysis`를 호출한다.

## GET `/health`

- HTTP Method: GET
- URL: `/health`
- 인증 필요 여부: 없음
- 요청 파라미터: 없음
- 연결 코드: `analysis-engine/app/api/health.py`

성공 응답:

```json
{ "status": "UP" }
```

오류 상태 코드: 구현된 별도 오류 없음

## POST `/api/basic-analysis`

- HTTP Method: POST
- URL: `/api/basic-analysis`
- 인증 필요 여부: 없음. 내부 네트워크 호출을 전제로 한다.
- 연결된 백엔드: `AnalysisPipelineService.runAnalysis` -> `AnalysisEngineClient.analyze`
- 연결된 analysis-engine 서비스: `BasicAnalyzer.analyze`
- 소유권 검증 여부: analysis-engine 자체에는 없음. 백엔드가 분석 작업 생성 전에 영상 소유권을 검증한다.

요청 JSON:

```json
{
  "jobId": 20,
  "videoId": 10,
  "filePath": "uploads/sample.mp4",
  "transcript": null
}
```

요청 필드:

| 필드 | 타입 | 조건 | 설명 |
|---|---|---|---|
| `jobId` | int | 1 이상 | 백엔드 `analysis_jobs.id` |
| `videoId` | int | 1 이상 | 백엔드 `uploaded_videos.id` |
| `filePath` | string | 빈 문자열 불가 | `STORAGE_ROOT` 기준 상대 경로 또는 절대 경로 |
| `transcript` | string/null | 선택 | 외부에서 제공한 transcript. 있으면 STT 대신 사용 |

성공 응답 예시:

```json
{
  "jobId": 20,
  "videoId": 10,
  "durationSeconds": 180.0,
  "videoInfo": {
    "durationSeconds": 180.0,
    "width": 1920,
    "height": 1080,
    "fps": 30.0,
    "sizeBytes": 10485760,
    "videoCodec": "h264",
    "audioCodec": "aac"
  },
  "transcript": "발표 텍스트",
  "stt": {
    "provider": "FASTER_WHISPER",
    "model": "base",
    "language": "ko",
    "text": "발표 텍스트",
    "segments": [],
    "confidence": 0.8,
    "available": true,
    "error": null
  },
  "metrics": {
    "speechSpeedSpm": 285.0,
    "fillerWordCount": 0,
    "fillerWordRatio": 0.0,
    "pauseCount": 2,
    "totalPauseSeconds": 1.8,
    "averagePauseSeconds": 0.9,
    "longestPauseSeconds": 1.1,
    "pauseRatio": 0.375,
    "speechSeconds": 3.0,
    "effectiveAudioSeconds": 4.8,
    "meanVolumeDb": -12.1,
    "volumeStdDevDb": 0.0,
    "volumeRangeDb": 0.0,
    "clippingRatio": 0.0,
    "volumeStabilityRatio": 1.0,
    "requestedVadProvider": "RMS",
    "actualVadProvider": "RMS",
    "vadFallbackUsed": false,
    "vadFallbackReason": null,
    "speechSegmentCount": 2,
    "audioVadProcessingTimeMs": 14.2,
    "audioMetricsFallback": false,
    "audioMetricsFallbackReason": null
  },
  "scores": [],
  "totalScore": 82.5,
  "summary": {},
  "observations": []
}
```

오류 상태 코드:

- 422: `MediaInfoError`. 영상 파일 없음, ffprobe JSON 오류 등
- 422: Pydantic 검증 실패
- 500: 처리 중 예기치 못한 오류

## 처리 흐름

1. `VideoInfoService.probe()`가 ffprobe로 길이, 해상도, 코덱을 읽는다.
2. `SttService`가 transcript 제공 여부에 따라 외부 텍스트 또는 faster-whisper STT를 사용한다.
3. `AudioAnalyzer`가 `AUDIO_VAD_PROVIDER` 설정에 따라 RMS 또는 선택형 Silero VAD로 발화/침묵 지표를 만든다.
4. `PoseAnalyzer`, `GazeAnalyzer`, `FaceAnalyzer`, `GestureAnalyzer`가 시각 지표를 만든다.
5. `ScoreCalculator`가 항목별 점수, 최종 점수, 요약, 관찰 피드백을 만든다.
