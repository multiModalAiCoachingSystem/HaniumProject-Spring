# 운영 Runbook

아래 명령은 프로젝트 루트의 PowerShell에서 실행한다.

```powershell
$compose = @(
  '--env-file', '.\infra\docker\.env',
  '-f', '.\infra\docker\docker-compose.yml'
)
```

## 설정 검증과 시작

```powershell
docker compose @compose config
docker compose @compose up -d --build --force-recreate
docker compose @compose ps
```

일반 운영 중 `down -v`는 사용하지 않는다.

## 특정 서비스 재시작

```powershell
docker compose @compose up -d --build backend
docker compose @compose up -d --build analysis-engine
docker compose @compose up -d --build video-llm-engine
docker compose @compose up -d --build frontend nginx
```

환경변수나 mount만 바꾼 경우에는 이미지 빌드보다 컨테이너 재생성이 핵심이다.

```powershell
docker compose @compose up -d --force-recreate backend analysis-engine video-llm-engine
```

## 상태와 로그

```powershell
docker compose @compose ps
docker compose @compose logs --tail 200 backend
docker compose @compose logs --tail 200 analysis-engine
docker compose @compose logs --tail 200 video-llm-engine
docker compose @compose logs --tail 200 nginx
docker compose @compose logs --tail 200 mysql
```

공유 mount 확인:

```powershell
docker compose @compose ps -q backend,analysis-engine,video-llm-engine |
  ForEach-Object { docker inspect $_ --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' }
```

세 서비스가 같은 호스트 Source를 `/app/storage`로 마운트해야 한다.

## 장애별 확인

### Nginx 502

`nginx`, `frontend`, `backend` 상태와 nginx 로그를 확인한다. `/api`는 `backend:8080`, 그 외는 `frontend:80`으로 전달된다.

### DB 연결 실패

MySQL health, `DB_*`, `MYSQL_*` 일치 여부와 기존 볼륨의 계정 비밀번호를 확인한다. `.env`의 `MYSQL_PASSWORD` 변경은 기존 볼륨의 계정 비밀번호를 자동으로 바꾸지 않는다.

### Ollama 연결 실패

호스트에서 `ollama list`를 확인하고 컨테이너의 `OLLAMA_BASE_URL`이 `host.docker.internal:11434`를 가리키는지 확인한다.

### NVIDIA fallback

API key 값은 출력하지 않고 SET/MISSING만 확인한다. video-llm-engine 로그의 `requestedProvider`, `actualProvider`, `actualModel`, `fallbackUsed`, `fallbackReason`을 확인한다.

### PROCESSING 정체

backend 로그, 두 엔진 로그, 원본 파일 존재와 외부 호출 timeout을 확인한다. backend 재시작 시 `QUEUED`, `PROCESSING`, `CANCEL_REQUESTED` 작업은 복구 Service가 처리하며 최대 횟수는 `ANALYSIS_RECOVERY_MAX_ATTEMPTS`다.

### 429

클라이언트의 반복 요청과 endpoint별 제한을 확인한다. 현재 제한은 backend 인스턴스 메모리 기반이므로 컨테이너 재시작과 다중 인스턴스에 걸쳐 공유되지 않는다.

## 백업과 복원

```powershell
.\scripts\backup\backup-all.ps1 -BackupRoot '<프로젝트-외부-백업-경로>'
```

복원은 [DB 및 Storage 백업·복원](backup-and-restore.md)을 따른다. 대상 목록과 영향 확인 없이 DB, 볼륨, 사용자 영상을 삭제하지 않는다.
