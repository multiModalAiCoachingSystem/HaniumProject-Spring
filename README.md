# presentation-coaching-ai

발표 영상을 업로드하면 영상, 음성, STT, Video LLM, 일반 LLM 결과를 통합해 발표 점수와 시간대별 코칭을 제공하는 멀티모달 발표 코칭 서비스입니다.

## 시스템 구조

- `frontend`: React + Vite 사용자 화면
- `backend`: Spring Boot 중심 API 서버, 인증, 업로드, 작업 상태, 결과 저장 담당
- `analysis-engine`: FastAPI 기반 영상/음성 기본 분석 서버
- `video-llm-engine`: Video LLM 관찰 결과 생성 서버
- `infra`: Docker Compose, MySQL, Nginx 설정
- `storage`: Docker 공유 볼륨 대상 디렉터리

## Windows 저장소 설정

PowerShell에서 다음 환경변수를 설정합니다.

```powershell
$env:HOST_STORAGE_PATH="D:/presentation-coaching-ai/storage"
```

컨테이너 내부 경로는 `/data`로 통일합니다.

## Mock AI 실행 흐름

초기 개발 단계에서는 실제 AI 모델 없이 다음 설정으로 전체 흐름을 테스트합니다.

```powershell
$env:VIDEO_LLM_PROVIDER="MOCK"
$env:LLM_PROVIDER="MOCK"
```

## Docker 실행

```powershell
cd .\infra\docker
docker compose --env-file ..\env\backend.env.example up --build
```

## 개별 실행

```powershell
cd backend
.\gradlew bootRun

cd ..\analysis-engine
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001

cd ..\video-llm-engine
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8002

cd ..\frontend
npm install
npm run dev
```

## 테스트 시나리오

1. 회원가입
2. 로그인
3. 500MB 이하 영상 업로드
4. 분석 요청
5. 상태 화면에서 단계 확인
6. Mock 분석 결과 저장
7. 결과 화면에서 영상, Radar Chart, 점수, 시간대별 피드백 확인
8. 시간 버튼으로 영상 위치 이동 확인
9. 다른 사용자 접근 차단 및 관리자 조회 확인

## MySQL Workbench 접속

Docker Compose 실행 후 Workbench에서 다음 값으로 접속합니다.

- Hostname: `127.0.0.1`
- Port: `3307`
- Username: `presentation`
- Password: `infra/env/backend.env.example`의 `DB_PASSWORD` 값을 참고하세요.
- Default Schema: `presentation_coaching_ai`

관리자 계정으로 접속해야 하면 Username은 `root`, Password는 `infra/env/backend.env.example`의 `MYSQL_ROOT_PASSWORD` 값을 참고하세요.


## 현재 구현 상태

- 회원가입: DB 저장, 이메일 중복 검사, 비밀번호 확인, BCrypt 암호화
- 로그인: 이메일/비밀번호 검증, Access Token 발급, Refresh Token HttpOnly Cookie 저장
- 영상 업로드: 로그인 사용자만 가능, 500MB 제한, MIME Type 검증, `/data/uploads` 저장
- 영상 스트리밍: 소유자 또는 관리자만 접근 가능, Range 요청 대응
- 분석 작업: DB에 `QUEUED` 생성 후 비동기 Mock 분석 단계 진행
- 결과 저장: 점수 평균 총점, Radar Chart용 점수, 시간대별 장점/개선점, 종합 피드백 저장
- 관리자: 사용자 목록, 분석 작업 목록, 시스템 Health 기본 조회

실제 FFmpeg, MediaPipe, Whisper, Video LLM 연동은 Mock Provider 뒤에 붙일 수 있도록 서버를 분리해둔 상태입니다.

## Ollama 모델 설정

이 프로젝트는 아래 Qwen 모델만 사용합니다.

```env
VIDEO_LLM_PROVIDER=OLLAMA
OLLAMA_BASE_URL=http://host.docker.internal:11434
TEXT_LLM_MODEL=qwen3:4b
VIDEO_LLM_MODEL=qwen3-vl:4b
```

- `TEXT_LLM_MODEL=qwen3:4b`: 텍스트 분석 결과 설명 및 최종 피드백
- `VIDEO_LLM_MODEL=qwen3-vl:4b`: 영상 프레임 이미지 판독
- `STT_PROVIDER=FASTER_WHISPER`: analysis-engine 안에서 발표 음성을 STT로 변환
- `WHISPER_MODEL=base`: 로컬 Whisper 모델명. 한국어 정확도를 높이려면 `small` 이상으로 변경 가능
- `STT_LANGUAGE=ko`: 발표 음성 기본 언어
- `HF_HOME=/data/models/huggingface`: Whisper 모델 캐시 저장 위치
