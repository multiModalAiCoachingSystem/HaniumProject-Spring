# 환경변수

실제 값이 비밀인 항목은 이 문서에 기록하지 않는다. 아래 표는 이름, 사용 위치, 역할만 정리한다.

## backend

| 환경변수 | 사용 위치 | 역할 |
|---|---|---|
| `DB_HOST` | `application.yml` | MySQL host |
| `DB_PORT` | `application.yml` | MySQL port |
| `DB_NAME` | `application.yml` | DB 이름 |
| `DB_USERNAME` | `application.yml` | DB 사용자 |
| `DB_PASSWORD` | `application.yml` | DB 비밀번호 |
| `STORAGE_ROOT` | `application.yml`, `VideoFile*Service` | 파일 저장 루트 |
| `ANALYSIS_ENGINE_BASE_URL` | `AnalysisEngineProperties` | analysis-engine URL |
| `VIDEO_LLM_ENGINE_BASE_URL` | `VideoLlmEngineProperties` | video-llm-engine URL |
| `TEXT_LLM_PROVIDER` | `TextLlmProperties` | Text LLM provider. 현재 호출 미구현 |
| `OLLAMA_BASE_URL` | `TextLlmProperties` | Ollama URL |
| `TEXT_LLM_MODEL` | `TextLlmProperties` | Text LLM 모델명 |
| `JWT_SECRET` | `JwtTokenProvider` | access token 서명 키 |

## analysis-engine

| 환경변수 | 사용 위치 | 역할 |
|---|---|---|
| `STORAGE_ROOT` | `VideoInfoService`, `SttService` | 파일 경로 기준 |
| `STT_PROVIDER` | `SttService` | `FASTER_WHISPER`, `WHISPER`, `MOCK`, `OFF` 등 |
| `WHISPER_MODEL` | `SttService` | faster-whisper 모델명 |
| `WHISPER_DEVICE` | `SttService` | `cpu` 등 |
| `WHISPER_COMPUTE_TYPE` | `SttService` | `int8` 등 |
| `STT_LANGUAGE` | `SttService` | STT 언어 |
| `FFMPEG_BIN` | `SttService` | ffmpeg 경로 |
| `FFPROBE_BIN` | `VideoInfoService` | ffprobe 경로 |
| `HF_HOME` | compose | 모델 캐시 경로 |

## video-llm-engine

| 환경변수 | 사용 위치 | 역할 |
|---|---|---|
| `STORAGE_ROOT` | `FFmpegService`, `Settings` | 파일 경로 기준 |
| `VIDEO_LLM_PROVIDER` | `VideoLlmAnalyzer` | `OLLAMA` 또는 mock |
| `OLLAMA_BASE_URL` | `QwenVlRunner` | Ollama URL |
| `VIDEO_LLM_MODEL` | `QwenVlRunner` | Qwen-VL 모델 |
| `TEXT_LLM_MODEL` | `Settings` | 텍스트 모델 설정값. 현재 video LLM 호출에는 사용하지 않음 |
| `FFMPEG_BIN` | `FFmpegService` | ffmpeg 경로 |
| `FFPROBE_BIN` | `FFmpegService` | ffprobe 경로 |

## frontend

`infra/env/frontend.env.example`에는 `VITE_API_BASE_URL=/api`가 있으나 현재 `frontend/src/api/httpClient.js`는 이 값을 읽지 않고 `baseURL: "/api"`를 하드코딩한다.
