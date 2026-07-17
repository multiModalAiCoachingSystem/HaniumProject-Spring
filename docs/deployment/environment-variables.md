# 환경변수

실제 비밀번호, `JWT_SECRET`, API Key는 문서나 Git 저장소에 기록하지 않는다. Docker 실행 시에는 `infra/docker/.env.example`을 `infra/docker/.env`로 복사한 뒤 사용자가 실제 값을 직접 채운다.

```powershell
Copy-Item .\infra\docker\.env.example .\infra\docker\.env
```

`infra/docker/.env`는 Git에 올라가지 않는다.

## Docker Compose 필수 변수

| 변수명 | 사용 위치 | 설명 | 예시값 |
|---|---|---|---|
| `HOST_STORAGE_PATH` | Docker bind mount | 호스트의 공유 저장소 경로. 프로젝트 내부 `storage`가 아니라 외부 D 드라이브 경로를 사용한다. | `D:/presentation-coaching-storage` |
| `STORAGE_ROOT` | backend, analysis-engine, video-llm-engine | 컨테이너 내부 공유 저장소 경로 | `/app/storage` |
| `HF_HOME` | analysis-engine | Hugging Face 모델 캐시 경로 | `/app/storage/models/huggingface` |
| `DB_NAME` | mysql, backend | MySQL 데이터베이스 이름 | `presentation_coaching_ai` |
| `DB_USERNAME` | mysql, backend | MySQL 애플리케이션 계정 | `presentation` |
| `MYSQL_PASSWORD` | mysql | `MYSQL_USER` 계정 초기 비밀번호 | `change-me` |
| `MYSQL_ROOT_PASSWORD` | mysql | MySQL root 계정 초기 비밀번호 | `change-root-password` |
| `DB_HOST` | backend | backend가 접속할 DB 호스트 | `mysql` |
| `DB_PORT` | backend | backend가 접속할 DB 포트 | `3306` |
| `DB_PASSWORD` | backend | backend DB 접속 비밀번호 | `change-me` |
| `JWT_SECRET` | backend | JWT 서명 키 | `replace-with-at-least-32-byte-secret` |
| `ANALYSIS_ENGINE_BASE_URL` | backend | analysis-engine 내부 URL | `http://analysis-engine:8001` |
| `VIDEO_LLM_ENGINE_BASE_URL` | backend | video-llm-engine 내부 URL | `http://video-llm-engine:8002` |
| `STT_PROVIDER` | analysis-engine | STT provider | `FASTER_WHISPER` |
| `WHISPER_MODEL` | analysis-engine | Whisper 모델명 | `base` |
| `WHISPER_DEVICE` | analysis-engine | Whisper 실행 장치 | `cpu` |
| `WHISPER_COMPUTE_TYPE` | analysis-engine | Whisper compute type | `int8` |
| `STT_LANGUAGE` | analysis-engine | STT 언어 | `ko` |
| `AUDIO_VAD_PROVIDER` | analysis-engine | 음성 구간 검출 방식. `RMS`, `SILERO`, `NONE` 중 선택 | `RMS` |
| `AUDIO_VAD_FALLBACK_PROVIDER` | analysis-engine | Silero 실패 시 fallback 방식 | `RMS` |
| `VAD_MIN_SPEECH_MS` | analysis-engine | speech segment로 인정할 최소 발화 길이(ms) | `250` |
| `VAD_MIN_SILENCE_MS` | analysis-engine | pause로 인정할 최소 침묵 길이(ms) | `350` |
| `VAD_SPEECH_PAD_MS` | analysis-engine | Silero speech segment 앞뒤 보정(ms) | `100` |
| `VAD_MERGE_GAP_MS` | analysis-engine | 가까운 speech segment 병합 기준(ms) | `200` |
| `SILERO_VAD_THRESHOLD` | analysis-engine | Silero speech probability threshold | `0.5` |
| `SILERO_VAD_MODEL_PATH` | analysis-engine | 오프라인 Silero JIT 모델 파일 경로. 비어 있으면 RMS fallback | 빈 값 |
| `TEXT_LLM_PROVIDER` | backend, video-llm-engine 환경 | Text LLM provider | `OLLAMA` |
| `TEXT_LLM_MODEL` | backend, video-llm-engine | Text LLM 모델명 | `qwen3:4b` |
| `TEXT_LLM_TIMEOUT_SECONDS` | backend | Text LLM timeout | `30` |
| `VIDEO_LLM_PROVIDER` | video-llm-engine | Video LLM provider. `NVIDIA_NIM`, `OLLAMA`, `NONE` 중 선택 | `NVIDIA_NIM` |
| `VIDEO_LLM_MODEL` | video-llm-engine | NVIDIA Video LLM 모델명 | `nvidia/nemotron-nano-12b-v2-vl` |
| `VIDEO_LLM_FALLBACK_PROVIDER` | video-llm-engine | NVIDIA 실패 시 fallback provider | `OLLAMA` |
| `OLLAMA_VIDEO_LLM_MODEL` | video-llm-engine | Ollama Video LLM fallback 모델명 | `qwen3-vl:4b` |
| `NVIDIA_API_BASE_URL` | video-llm-engine | NVIDIA OpenAI-compatible Chat Completions endpoint | `https://integrate.api.nvidia.com/v1/chat/completions` |
| `NVIDIA_API_KEY` | video-llm-engine | NVIDIA API Key. 실제 값은 `.env`에만 저장 | 빈 값 |
| `NVIDIA_REQUEST_TIMEOUT_SECONDS` | video-llm-engine | NVIDIA 요청 timeout | `180` |
| `NVIDIA_MAX_TOKENS` | video-llm-engine | NVIDIA 응답 token 제한 | `4096` |
| `NVIDIA_TEMPERATURE` | video-llm-engine | NVIDIA temperature | `0.2` |
| `NVIDIA_TOP_P` | video-llm-engine | NVIDIA top_p | `1.0` |
| `NVIDIA_MAX_IMAGES` | video-llm-engine | NVIDIA로 보낼 최대 프레임 수. NVIDIA API 제한을 고려해 내부 상한은 10 | `8` |
| `NVIDIA_MAX_PAYLOAD_MB` | video-llm-engine | NVIDIA 요청 JSON payload 제한 | `20` |
| `OLLAMA_BASE_URL` | backend, video-llm-engine | Ollama API URL | `http://host.docker.internal:11434` |

`DB_PASSWORD`는 실제 MySQL `presentation` 계정 비밀번호와 같아야 한다. 기존 `mysql-data` 볼륨이 이미 생성된 뒤 `.env`의 `MYSQL_PASSWORD`를 바꿔도 MySQL 계정 비밀번호는 자동 변경되지 않는다.

## 저장소 경로 정책

- `HOST_STORAGE_PATH`는 반드시 프로젝트 외부 경로를 사용한다.
- 기본 예시 경로는 `D:/presentation-coaching-storage`이다.
- `D:/presentation-coaching-ai/storage`는 새 작업 저장 위치로 사용하지 않는다.
- compose는 `HOST_STORAGE_PATH`가 누락되면 시작 전에 오류를 낸다.
- backend, analysis-engine, video-llm-engine는 모두 같은 host 경로를 `/app/storage`로 마운트한다.

## 예약 변수

| 변수명 | 현재 상태 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | 현재 실제 호출 경로에서 사용하지 않음 | 추후 OpenAI 연동 시 환경변수 또는 secret manager로만 주입 |
| `OPENAI_MODEL` | 현재 실제 호출 경로에서 사용하지 않음 | 추후 OpenAI 연동 시 사용할 모델명 |
