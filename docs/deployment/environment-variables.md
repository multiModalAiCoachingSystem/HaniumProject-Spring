# 환경변수

실제 비밀번호, `JWT_SECRET`, API Key는 문서나 Git 저장소에 기록하지 않는다. Docker 실행 시에는 `infra/docker/.env.example`을 `infra/docker/.env`로 복사한 뒤 사용자가 실제 값을 직접 채운다.

```powershell
Copy-Item .\infra\docker\.env.example .\infra\docker\.env
```

`infra/docker/.env`는 Git에 올라가지 않는다.

## Docker Compose 필수 변수

| 변수명 | 사용 위치 | 설명 | 예시값 |
|---|---|---|---|
| `APP_TIME_ZONE` | mysql, backend | DB `DATETIME`, JVM, Hibernate, Jackson에서 사용하는 서비스 표준 timezone | `Asia/Seoul` |
| `HOST_STORAGE_PATH` | Docker bind mount | 호스트의 공유 저장소 경로. 프로젝트 내부가 아닌 기존 디렉터리 사용 | `<프로젝트-외부-storage-경로>` |
| `STORAGE_ROOT` | backend, analysis-engine, video-llm-engine | 컨테이너 내부 공유 저장소 경로 | `/app/storage` |
| `ANALYSIS_RECOVERY_MAX_ATTEMPTS` | backend | backend 재시작 시 job 자동 복구 최대 횟수 | `3` |
| `HF_HOME` | analysis-engine | Hugging Face 모델 캐시 경로 | `/app/storage/models/huggingface` |
| `DB_NAME` | mysql, backend | MySQL 데이터베이스 이름 | `presentation_coaching_ai` |
| `DB_USERNAME` | mysql, backend | MySQL 애플리케이션 계정 | `presentation` |
| `MYSQL_PASSWORD` | mysql | `MYSQL_USER` 계정 초기 비밀번호 | `change-me` |
| `MYSQL_ROOT_PASSWORD` | mysql | MySQL root 계정 초기 비밀번호 | `change-root-password` |
| `DB_HOST` | backend | backend가 접속할 DB 호스트 | `mysql` |
| `DB_PORT` | backend | backend가 접속할 DB 포트 | `3306` |
| `DB_PASSWORD` | backend | backend DB 접속 비밀번호 | `change-me` |
| `FLYWAY_BASELINE_ON_MIGRATE` | backend | 기존 DB 최초 도입 1회에만 `true`. 평상시에는 반드시 `false` | `false` |
| `FLYWAY_BASELINE_VERSION` | backend | 기존 스키마의 baseline 버전 | `1` |
| `JWT_SECRET` | backend | JWT 서명 키 | `replace-with-at-least-32-byte-secret` |
| `APP_ENV` | backend | `development` 또는 `production` 보안 정책 선택 | `development` |
| `APP_BASE_URL` | backend | 서비스의 외부 기준 URL. 운영에서는 HTTPS 필수 | `http://localhost` |
| `FRONTEND_ORIGINS` | backend | 쉼표로 구분한 정확한 CORS 허용 origin | `http://localhost:5173,http://localhost` |
| `COOKIE_SECURE` | backend | refresh cookie의 Secure 속성 | `false` |
| `COOKIE_SAME_SITE` | backend | refresh cookie SameSite 속성 | `Lax` |
| `TRUST_PROXY_HEADERS` | backend | 신뢰하는 reverse proxy의 client IP header 사용 여부 | `false` |
| `RATE_LIMIT_LOGIN_PER_MINUTE` | backend | 로그인 IP별 분당 제한 | `10` |
| `RATE_LIMIT_REGISTER_PER_HOUR` | backend | 회원가입 IP별 시간당 제한 | `5` |
| `RATE_LIMIT_REFRESH_PER_MINUTE` | backend | token refresh IP별 분당 제한 | `20` |
| `RATE_LIMIT_UPLOAD_PER_HOUR` | backend | 사용자별 영상 업로드 시간당 제한 | `20` |
| `RATE_LIMIT_ANALYSIS_PER_HOUR` | backend | 사용자별 분석 시작 시간당 제한 | `20` |
| `RATE_LIMIT_RETRY_PER_HOUR` | backend | 사용자별 재분석 시간당 제한 | `10` |
| `RATE_LIMIT_ADMIN_PER_MINUTE` | backend | 관리자 principal별 분당 제한 | `60` |
| `RATE_LIMIT_API_PER_MINUTE` | backend | 그 외 API principal/IP별 분당 제한 | `120` |
| `TLS_CERTIFICATE_PATH` | 운영 nginx override | 호스트 인증서 파일 경로 | 저장소 외부 경로 |
| `TLS_PRIVATE_KEY_PATH` | 운영 nginx override | 호스트 개인키 파일 경로 | 저장소 외부 경로 |
| `ANALYSIS_ENGINE_BASE_URL` | backend | analysis-engine 내부 URL | `http://analysis-engine:8001` |
| `VIDEO_LLM_ENGINE_BASE_URL` | backend | video-llm-engine 내부 URL | `http://video-llm-engine:8002` |
| `STT_PROVIDER` | analysis-engine | STT provider | `FASTER_WHISPER` |
| `WHISPER_MODEL` | analysis-engine | Whisper 모델명 | `base` |
| `WHISPER_DEVICE` | analysis-engine | Whisper 실행 장치 | `cpu` |
| `WHISPER_COMPUTE_TYPE` | analysis-engine | Whisper compute type | `int8` |
| `STT_LANGUAGE` | analysis-engine | STT 언어 | `ko` |
| `FACE_DETECTION_CONFIDENCE` | analysis-engine | FaceMesh 최소 감지 confidence | `0.3` |
| `FACE_TRACKING_CONFIDENCE` | analysis-engine | FaceMesh 최소 tracking confidence | `0.3` |
| `FACE_ANALYSIS_MAX_WIDTH` | analysis-engine | 얼굴 분석 입력 프레임 최대 너비 | `1280` |
| `FACE_CROP_RETRY_ENABLED` | analysis-engine | 전체 프레임 실패 시 Haar 얼굴 crop retry | `true` |
| `FACE_CROP_PADDING_RATIO` | analysis-engine | 얼굴 crop 주변 여백 비율 | `0.25` |
| `FACE_CROP_MIN_SIZE` | analysis-engine | retry crop 최소 변 길이 | `256` |
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
| `BACKUP_ROOT` | PowerShell backup scripts | 프로젝트·storage 외부의 백업 루트. 실제 경로는 `.env`에만 기록 | `<프로젝트-외부-백업-경로>` |
| `BACKUP_RETENTION_DAYS` | PowerShell backup scripts | 백업 보관 기간 | `14` |
| `BACKUP_KEEP_MINIMUM` | PowerShell backup scripts | 기간과 무관하게 유지할 최소 백업 수 | `3` |
| `BACKUP_INCLUDE_TEMP_ARTIFACTS` | PowerShell backup scripts | `video-llm`, `tmp` 임시 산출물 포함 여부 | `false` |

`DB_PASSWORD`는 실제 MySQL `presentation` 계정 비밀번호와 같아야 한다. 기존 `mysql-data` 볼륨이 이미 생성된 뒤 `.env`의 `MYSQL_PASSWORD`를 바꿔도 MySQL 계정 비밀번호는 자동 변경되지 않는다.

Flyway와 백업 운영 절차는 [Flyway 마이그레이션 운영](../database/flyway-migrations.md), [DB 및 Storage 백업·복원](backup-and-restore.md)을 참고한다.

## 시간 저장 및 표시 정책

- 기존 DB는 timezone 정보가 없는 MySQL `DATETIME` 컬럼에 한국 현지시각을 저장한다.
- 기존 데이터 호환성을 위해 엔티티는 `LocalDateTime`, DB는 KST wall-clock `DATETIME` 정책을 유지한다.
- backend JVM, Hibernate JDBC, Jackson, MySQL session은 `Asia/Seoul`로 통일한다.
- API 응답 DTO는 `OffsetDateTime`을 사용해 `2026-07-21T16:28:00+09:00`처럼 offset을 포함한다.
- frontend는 API offset을 기준으로 파싱하고 `Intl.DateTimeFormat`의 `Asia/Seoul` timezone으로 표시한다.
- 기존 DB 값에 `+9시간` 또는 `-9시간` migration을 수행하지 않는다.

## 저장소 경로 정책

- `HOST_STORAGE_PATH`는 반드시 프로젝트 외부 경로를 사용한다.
- `.env.example`의 경로는 Windows 개발 환경용 예시이며 실제 환경에 맞게 변경한다.
- 프로젝트 내부 `storage`는 새 작업 저장 위치로 사용하지 않는다.
- compose는 `HOST_STORAGE_PATH`가 누락되면 시작 전에 오류를 낸다.
- backend, analysis-engine, video-llm-engine는 모두 같은 host 경로를 `/app/storage`로 마운트한다.

## 예약 변수

| 변수명 | 현재 상태 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | 현재 실제 호출 경로에서 사용하지 않음 | 추후 OpenAI 연동 시 환경변수 또는 secret manager로만 주입 |
| `OPENAI_MODEL` | 현재 실제 호출 경로에서 사용하지 않음 | 추후 OpenAI 연동 시 사용할 모델명 |
