# 환경변수

실제 비밀번호, `JWT_SECRET`, API Key는 문서와 저장소에 기록하지 않는다. Docker 실행 시에는 `infra/docker/.env.example`을 복사해 `infra/docker/.env`를 만들고, 실제 값은 사용자가 직접 채운다.

```powershell
Copy-Item .\infra\docker\.env.example .\infra\docker\.env
```

`infra/docker/.env`는 Git에 올리지 않는다.

## Docker Compose 필수 변수

| 변수명 | 사용 위치 | 설명 | 예시값 |
|---|---|---|---|
| `HOST_STORAGE_PATH` | `infra/docker/docker-compose.yml` volume | 호스트의 파일 저장 경로. 상대 경로는 compose 파일 위치인 `infra/docker` 기준이다. | `../../storage` |
| `DB_NAME` | mysql, backend | MySQL 데이터베이스 이름 | `presentation_coaching_ai` |
| `DB_USERNAME` | mysql, backend | MySQL 애플리케이션 계정 | `presentation` |
| `MYSQL_PASSWORD` | mysql | `MYSQL_USER` 계정 초기 비밀번호 | `change-me` |
| `MYSQL_ROOT_PASSWORD` | mysql | MySQL root 계정 초기 비밀번호 | `change-root-password` |
| `DB_HOST` | backend | backend가 접속할 DB 호스트 | `mysql` |
| `DB_PORT` | backend | backend가 접속할 DB 포트 | `3306` |
| `DB_PASSWORD` | backend | backend DB 접속 비밀번호 | `change-me` |
| `JWT_SECRET` | backend | JWT 서명 키 | `replace-with-at-least-32-byte-secret` |
| `STORAGE_ROOT` | backend, analysis-engine, video-llm-engine | 컨테이너 내부 공유 저장소 경로 | `/data` |
| `ANALYSIS_ENGINE_BASE_URL` | backend | analysis-engine 내부 URL | `http://analysis-engine:8001` |
| `VIDEO_LLM_ENGINE_BASE_URL` | backend | video-llm-engine 내부 URL | `http://video-llm-engine:8002` |
| `STT_PROVIDER` | analysis-engine | STT provider | `FASTER_WHISPER` |
| `WHISPER_MODEL` | analysis-engine | Whisper 모델명 | `base` |
| `WHISPER_DEVICE` | analysis-engine | Whisper 실행 장치 | `cpu` |
| `WHISPER_COMPUTE_TYPE` | analysis-engine | Whisper compute type | `int8` |
| `STT_LANGUAGE` | analysis-engine | STT 언어 | `ko` |
| `HF_HOME` | analysis-engine | Hugging Face 모델 캐시 경로 | `/data/models/huggingface` |
| `TEXT_LLM_PROVIDER` | backend, video-llm-engine 환경 | Text LLM provider 설정값 | `OLLAMA` |
| `TEXT_LLM_MODEL` | backend, video-llm-engine | Text LLM 모델명 | `qwen3:4b` |
| `VIDEO_LLM_PROVIDER` | video-llm-engine | Video LLM provider | `OLLAMA` |
| `VIDEO_LLM_MODEL` | video-llm-engine | Video LLM 모델명 | `qwen3-vl:4b` |
| `OLLAMA_BASE_URL` | backend, video-llm-engine | Ollama API URL | `http://host.docker.internal:11434` |

`DB_PASSWORD`는 실제 MySQL의 `presentation` 계정 비밀번호와 같아야 한다. Docker 초기 구동 시에는 보통 `MYSQL_PASSWORD`와 `DB_PASSWORD`를 같은 값으로 둔다.

## 예비 변수

| 변수명 | 현재 상태 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | 현재 Docker Compose와 코드 실행 경로에서 미사용 | 추후 OpenAI 연동 시 사용할 수 있는 API Key |
| `OPENAI_MODEL` | 현재 Docker Compose와 코드 실행 경로에서 미사용 | 추후 OpenAI 연동 시 사용할 수 있는 모델명 |

현재 프로젝트는 OpenAI API를 실제 호출하지 않고 Ollama 기반 `qwen3:4b`, `qwen3-vl:4b` 설정을 사용한다.

## 서비스별 사용 위치

### backend

`backend/src/main/resources/application.yml`과 관련 설정 클래스에서 다음 값을 사용한다.

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `STORAGE_ROOT`
- `ANALYSIS_ENGINE_BASE_URL`
- `VIDEO_LLM_ENGINE_BASE_URL`
- `TEXT_LLM_PROVIDER`
- `OLLAMA_BASE_URL`
- `TEXT_LLM_MODEL`
- `JWT_SECRET`

### analysis-engine

Python 서비스에서 다음 값을 사용한다.

- `STORAGE_ROOT`
- `STT_PROVIDER`
- `WHISPER_MODEL`
- `WHISPER_DEVICE`
- `WHISPER_COMPUTE_TYPE`
- `STT_LANGUAGE`
- `FFMPEG_BIN`
- `FFPROBE_BIN`

`FFMPEG_BIN`, `FFPROBE_BIN`은 Dockerfile에서 설치한 기본 실행 파일을 쓰므로 Docker Compose `.env`에는 필수로 넣지 않았다.

### video-llm-engine

Python 서비스에서 다음 값을 사용한다.

- `STORAGE_ROOT`
- `VIDEO_LLM_PROVIDER`
- `OLLAMA_BASE_URL`
- `VIDEO_LLM_MODEL`
- `TEXT_LLM_MODEL`
- `FFMPEG_BIN`
- `FFPROBE_BIN`

`TEXT_LLM_PROVIDER`는 compose 환경에 함께 전달하지만 현재 video-llm-engine 코드에서는 직접 사용하지 않는다.

### frontend

현재 `frontend/src/api/httpClient.js`는 `baseURL: "/api"`를 사용한다. `infra/env/frontend.env.example`의 `VITE_API_BASE_URL=/api`는 현재 프론트 빌드에서 직접 사용되지 않는다.

## 기존 MySQL 볼륨 주의사항

`mysql-data` 볼륨이 이미 생성된 뒤에는 `.env`의 `MYSQL_PASSWORD`나 `MYSQL_ROOT_PASSWORD`를 바꿔도 기존 MySQL 계정 비밀번호가 자동으로 변경되지 않는다.

기존 볼륨을 계속 사용할 때는 다음을 확인해야 한다.

- `DB_PASSWORD`는 실제 MySQL `presentation` 계정 비밀번호와 같아야 한다.
- 기존 DB 계정 비밀번호를 바꾸려면 MySQL 내부에서 별도로 `ALTER USER` 등으로 변경해야 한다.
- 이번 문서 작업에서는 DB 볼륨 삭제나 데이터 초기화를 수행하지 않는다.
