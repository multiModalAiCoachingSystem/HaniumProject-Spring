# Video LLM 역할

## 목적

`video-llm-engine`은 발표 영상에서 대표 프레임과 오디오 파일을 추출하고, `VIDEO_LLM_PROVIDER=OLLAMA`일 때 Qwen-VL 모델로 시각 관찰 피드백을 생성한다.

## 구현 위치

- API: `video-llm-engine/app/api/video_llm_analysis.py`
- 분석 조율: `video-llm-engine/app/services/video_llm_analyzer.py`
- 샘플 준비: `video-llm-engine/app/services/video_sampler.py`
- ffmpeg 처리: `video-llm-engine/app/services/ffmpeg_service.py`
- Ollama 호출: `video-llm-engine/app/model/qwen_vl_runner.py`
- 설정: `video-llm-engine/app/model/model_config.py`

## 입력

```json
{
  "jobId": 20,
  "videoId": 10,
  "filePath": "uploads/sample.mp4"
}
```

`filePath`는 `STORAGE_ROOT` 기준 상대 경로 또는 절대 경로이다.

## 처리

1. ffprobe로 `VideoMetadata`를 읽는다.
2. ffmpeg로 최대 8개 프레임을 `/data/video-llm/job-{jobId}/frames`에 저장한다.
3. ffmpeg로 16kHz mono wav를 `/data/video-llm/job-{jobId}/audio/speech.wav`에 저장한다.
4. provider가 `OLLAMA`이면 첫 4개 프레임 이미지를 base64로 인코딩해 Ollama `/api/generate`에 보낸다.
5. 모델 응답의 `observations`를 정규화한다.

## 현재 사용하는 모델과 환경변수

| 환경변수 | 기본값 | 역할 |
|---|---|---|
| `VIDEO_LLM_PROVIDER` | `MOCK` | `OLLAMA`이면 실제 Qwen-VL 호출 |
| `OLLAMA_BASE_URL` | `http://host.docker.internal:11434` | Ollama 서버 |
| `VIDEO_LLM_MODEL` | `qwen3-vl:4b` | 영상 프레임 분석 모델 |
| `TEXT_LLM_MODEL` | `qwen3:4b` | 텍스트 LLM용 설정값. video-llm-engine에서는 응답에 직접 사용하지 않음 |
| `STORAGE_ROOT` | `/data` | 영상/프레임/오디오 경로 기준 |

## 출력

`observations`는 백엔드에서 analysis-engine의 관찰 피드백 뒤에 병합되어 `feedback_json`에 저장된다. 현재 `totalScore` 계산에는 반영되지 않는다.

## 한계

Qwen-VL은 샘플 프레임만 본다. 영상 전체 움직임이나 실제 발화 의미와 프레임을 정밀 동기화하는 로직은 미구현이다.
