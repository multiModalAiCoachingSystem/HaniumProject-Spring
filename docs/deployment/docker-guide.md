# Docker 실행 가이드

Docker Compose 파일은 `infra/docker/docker-compose.yml`이다. 서비스는 `mysql`, `analysis-engine`, `video-llm-engine`, `backend`, `frontend`, `nginx`로 구성된다.

## 1. 환경변수 파일 준비

프로젝트 루트에서 예시 파일을 복사한다.

```powershell
Copy-Item .\infra\docker\.env.example .\infra\docker\.env
```

`infra/docker/.env`의 실제 비밀번호와 `JWT_SECRET`은 사용자가 직접 채운다. 이 파일은 Git에 커밋하지 않는다.

NVIDIA Nemotron Video LLM을 사용하려면 `infra/docker/.env`에 `VIDEO_LLM_PROVIDER=NVIDIA_NIM`과 실제 `NVIDIA_API_KEY`를 설정한다. `.env.example`에는 실제 키를 적지 않는다.

## 2. 저장소 경로

업로드 영상, 변환 영상, 분석 임시 파일, Video LLM 프레임/오디오 파일은 Docker bind mount로 공유한다.

| 대상 | 경로 |
|---|---|
| 호스트 저장소 | `HOST_STORAGE_PATH=D:/presentation-coaching-storage` |
| 컨테이너 내부 저장소 | `STORAGE_ROOT=/app/storage` |
| Compose mount | `${HOST_STORAGE_PATH}:/app/storage` |

`D:/presentation-coaching-ai/storage`는 새 작업 저장 위치로 사용하지 않는다. `HOST_STORAGE_PATH`가 누락되면 compose가 시작 전에 오류를 낸다.

## 3. 설정 검증

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  config
```

`config` 출력에는 비밀번호가 포함될 수 있으므로 공유하거나 문서에 붙여 넣지 않는다.

## 4. 전체 실행

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --build
```

브라우저 접속 주소는 nginx 기준 `http://localhost`이다.

## 5. 컨테이너만 재생성

저장소 mount와 환경변수만 바뀐 경우에는 이미지 재빌드보다 컨테이너 재생성이 핵심이다. 코드나 Dockerfile을 바꾸지 않았다면 다음 명령으로 충분하다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --force-recreate backend analysis-engine video-llm-engine
```

Video LLM provider 코드가 변경된 경우에는 `video-llm-engine` 이미지를 재빌드해야 한다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --build video-llm-engine
```

## 6. Mount 확인

```powershell
docker inspect presentation-coaching-ai-backend-1 --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
docker inspect presentation-coaching-ai-analysis-engine-1 --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
docker inspect presentation-coaching-ai-video-llm-engine-1 --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
```

세 서비스 모두 `D:\presentation-coaching-storage -> /app/storage`를 보여야 한다.

## 7. 기존 MySQL 볼륨 주의사항

MySQL 컨테이너가 처음 실행될 때 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DB_NAME`, `DB_USERNAME`으로 초기 계정과 DB를 만든다. 이미 `mysql-data` 볼륨이 있으면 `.env` 값을 바꿔도 기존 DB 계정 비밀번호는 자동 변경되지 않는다.

데이터를 보존해야 하는 경우 `mysql-data` 볼륨을 삭제하지 않는다.

## 8. 분석 복구 컬럼

기존 MySQL 볼륨을 `spring.profiles.active=prod`로 실행하기 전에는 다음 migration을 한 번 적용한다.

`infra/mysql/migrations/20260724_add_analysis_recovery_columns.sql`

기본 Docker 개발 설정은 `ddl-auto=update`이므로 backend 최초 재기동 시 같은 컬럼을 자동으로 추가한다.
migration은 컬럼 존재 여부를 확인하므로 중복 실행해도 기존 컬럼이나 분석 결과를 삭제하지 않는다.

backend 시작 로그의 다음 숫자로 복구 대상 수를 확인할 수 있다.

```text
Backend recovery started: queuedCount=0 processingCount=0 cancelRequestedCount=0
```
