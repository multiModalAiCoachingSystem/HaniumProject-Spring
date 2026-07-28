# 로컬 테스트 가이드

## 전체 검증

프로젝트 루트의 PowerShell에서 실행한다.

```powershell
.\scripts\ci\test-all.ps1
```

이 명령은 의존성 설치를 포함해 Backend, Frontend, 두 Python 엔진, Compose 설정을 순서대로 검증하며 첫 실패에서 중단한다.

## 서비스별 명령

### Backend

```powershell
.\scripts\ci\test-backend.ps1
```

직접 실행할 때의 실제 명령은 다음과 같다.

```powershell
cd .\backend
.\gradlew.bat clean test --no-daemon
.\gradlew.bat clean build --no-daemon
.\gradlew.bat bootJar --no-daemon
.\gradlew.bat build --warning-mode all --no-daemon
```

### Frontend

```powershell
.\scripts\ci\test-frontend.ps1
```

이 스크립트는 `npm ci`, `npm run lint`, `npm test`, `npm run build`를 실행한다. 현재 테스트 runner는 Node 내장 runner이므로 Jest/Vitest 전용 `--run` 옵션은 전달하지 않는다.

### Analysis Engine

```powershell
.\scripts\ci\test-analysis-engine.ps1
```

FFmpeg, OpenCV, MediaPipe import를 먼저 확인한 뒤 `python -m unittest discover -s tests -v`와 `python -m compileall -q app`을 실행한다.

### Video LLM Engine

```powershell
.\scripts\ci\test-video-llm-engine.ps1
```

스크립트 실행 중 Provider를 `NONE`으로 설정하고 기존 로컬 환경변수는 종료 시 복원한다.

### Docker Compose

```powershell
.\scripts\ci\validate-compose.ps1
```

기본적으로 비밀정보가 없는 `infra/docker/.env.ci.example`을 사용한다. 실제 개발 환경 구성을 검사할 때만 다음처럼 명시한다.

```powershell
.\scripts\ci\validate-compose.ps1 -EnvFile .\infra\docker\.env
```

실제 `.env` 값이나 전체 Compose 렌더링 결과를 CI 로그에 출력하지 않는다.

## 로컬 요구사항

- JDK 21
- Node.js 22 LTS 권장
- Python 3.12 권장
- FFmpeg
- Docker Desktop과 Compose v2

Analysis Engine 의존성은 해당 엔진의 `requirements.txt`로 설치되어 있어야 한다. CI는 Linux 시스템 패키지 `ffmpeg`, `libgl1`, `libglib2.0-0`, `libgomp1`을 설치한다.
