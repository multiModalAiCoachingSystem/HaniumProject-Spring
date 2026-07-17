# Video LLM 역할

## 목적

`video-llm-engine`은 발표 영상에서 대표 프레임과 오디오 파일을 추출하고, 선택된 Video LLM provider로 시각 관찰 피드백을 생성한다.

현재 지원 provider는 다음과 같다.

- `NVIDIA_NIM`: NVIDIA OpenAI-compatible Chat Completions API로 `nvidia/nemotron-nano-12b-v2-vl` 호출
- `OLLAMA`: 기존 Ollama `qwen3-vl:4b` 호출
- `NONE`: 외부 Video LLM 없이 결정적 fallback observation 반환

최종 한국어 발표 피드백은 backend의 Text LLM(`qwen3:4b`)이 담당한다. Nemotron은 최종 한국어 코칭 문장을 만들지 않고, 영상 프레임에서 직접 관찰 가능한 시각 행동만 구조화한다.

## 구현 위치

- API: `video-llm-engine/app/api/video_llm_analysis.py`
- 분석 조율: `video-llm-engine/app/services/video_llm_analyzer.py`
- 샘플 준비: `video-llm-engine/app/services/video_sampler.py`
- ffmpeg 처리: `video-llm-engine/app/services/ffmpeg_service.py`
- NVIDIA 호출: `video-llm-engine/app/model/nvidia_nemotron_runner.py`
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

## 처리 흐름

1. ffprobe로 `VideoMetadata`를 읽는다.
2. ffmpeg로 대표 프레임을 `STORAGE_ROOT/video-llm/job-{jobId}/frames`에 저장한다.
3. ffmpeg로 16kHz mono wav를 `STORAGE_ROOT/video-llm/job-{jobId}/audio/speech.wav`에 저장한다.
4. `VIDEO_LLM_PROVIDER`에 따라 provider를 선택한다.
5. `NVIDIA_NIM`이면 최대 16개 프레임을 `image_url` data URL 배열로 NVIDIA API에 보낸다.
6. `OLLAMA`이면 기존 `qwen3-vl:4b` 경로로 프레임 이미지를 보낸다.
7. 모델 응답을 backend가 읽는 `observations[]` 구조로 정규화한다.
8. NVIDIA 실패 시 `VIDEO_LLM_FALLBACK_PROVIDER=OLLAMA`이면 Ollama로 재시도하고, 그 외에는 결정적 fallback을 사용한다.

## 프레임 입력 방식

1차 구현은 원본 영상을 직접 전송하지 않는다. 500MB까지 허용되는 원본 영상을 base64로 전송하면 요청 크기, timeout, 메모리 사용량, 개인정보 전송 범위가 커지기 때문이다.

현재 방식은 다음과 같다.

- Video LLM용 프레임: 최대 16장
- 기본 샘플링: `VideoSampler.prepare_samples(..., max_frames=8)`
- `max_frames=768`이 들어와도 `VIDEO_LLM_MAX_FRAMES=16`으로 제한
- NVIDIA 요청에는 `data:image/jpeg;base64,...` 형태의 `image_url` 사용
- 원본 MP4 `video_url` 직접 전송은 미구현

## 환경변수

| 환경변수 | 예시값 | 역할 |
|---|---|---|
| `VIDEO_LLM_PROVIDER` | `NVIDIA_NIM` | `NVIDIA_NIM`, `OLLAMA`, `NONE` 중 선택 |
| `VIDEO_LLM_MODEL` | `nvidia/nemotron-nano-12b-v2-vl` | NVIDIA provider 모델 ID |
| `VIDEO_LLM_FALLBACK_PROVIDER` | `OLLAMA` | NVIDIA 실패 시 fallback provider |
| `NVIDIA_API_BASE_URL` | `https://integrate.api.nvidia.com/v1/chat/completions` | NVIDIA OpenAI-compatible endpoint |
| `NVIDIA_API_KEY` | 빈 값 | 실제 키는 `infra/docker/.env`에만 저장 |
| `NVIDIA_REQUEST_TIMEOUT_SECONDS` | `180` | NVIDIA 요청 timeout |
| `NVIDIA_MAX_TOKENS` | `4096` | 응답 token 제한 |
| `NVIDIA_TEMPERATURE` | `0.2` | 생성 온도 |
| `NVIDIA_TOP_P` | `1.0` | nucleus sampling 값 |
| `NVIDIA_MAX_IMAGES` | `8` | NVIDIA로 보낼 최대 이미지 수. 내부 상한은 10 |
| `NVIDIA_MAX_PAYLOAD_MB` | `20` | JSON 요청 payload 제한 |
| `OLLAMA_BASE_URL` | `http://host.docker.internal:11434` | Ollama 서버 |
| `OLLAMA_VIDEO_LLM_MODEL` | `qwen3-vl:4b` | Ollama Video LLM fallback 모델 |
| `TEXT_LLM_MODEL` | `qwen3:4b` | Text LLM 모델. Video LLM provider와 분리 |
| `STORAGE_ROOT` | `/app/storage` | 영상/프레임/오디오 경로 기준 |

## 출력

`observations`는 백엔드에서 analysis-engine의 관찰 피드백 뒤에 병합되어 `feedback_json`에 저장된다. Video LLM 결과는 현재 `totalScore` 계산에는 반영되지 않는다.

NVIDIA 원본 구조화 결과의 요약과 `visualMetrics`는 `metadata.videoLlmResult`에 저장된다. backend 호환성을 위해 `observations[]`는 기존 `type`, `category`, `startSeconds`, `endSeconds`, `title`, `description`, `suggestion` 구조를 유지한다.

## 한계

샘플 프레임 기반 분석이므로 영상 전체 움직임을 완전하게 추적하지 않는다. 실제 자세/시선/제스처 정량 점수는 analysis-engine의 MediaPipe/ScoreCalculator 결과를 우선한다.
