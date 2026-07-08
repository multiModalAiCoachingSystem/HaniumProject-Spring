# Docker 실행 가이드

Docker Compose 파일은 `infra/docker/docker-compose.yml`이다. 서비스는 `mysql`, `analysis-engine`, `video-llm-engine`, `backend`, `frontend`, `nginx`로 구성된다.

## 1. 환경변수 파일 준비

프로젝트 루트에서 예시 파일을 복사한다.

```powershell
Copy-Item .\infra\docker\.env.example .\infra\docker\.env
```

`infra/docker/.env`의 실제 값은 사용자가 직접 채운다. 특히 다음 값은 저장소에 커밋하지 않는다.

- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `DB_PASSWORD`
- `JWT_SECRET`
- `OPENAI_API_KEY`

## 2. 전체 실행

프로젝트 루트에서 실행한다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --build
```

브라우저 접속 주소는 nginx 기준 `http://localhost`이다.

## 3. backend만 재빌드

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --build backend
```

## 4. 설정 검증

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  config
```

`config` 출력에 비밀번호가 그대로 보일 수 있으므로 공유하거나 문서에 붙여 넣지 않는다.

## 5. 볼륨과 파일 저장소

| 대상 | 위치 |
|---|---|
| MySQL 데이터 | Docker volume `mysql-data` |
| 업로드 영상, 변환 영상, 분석 임시 파일 | `${HOST_STORAGE_PATH}:/data` |

`HOST_STORAGE_PATH`는 호스트 경로이고, 컨테이너 내부에서는 `STORAGE_ROOT=/data`로 접근한다. 상대 경로는 compose 파일 위치인 `infra/docker` 기준이므로, 프로젝트 루트의 `storage`를 쓰려면 `../../storage`를 사용한다. backend, analysis-engine, video-llm-engine은 같은 저장소를 공유해야 한다.

## 6. 기존 MySQL 볼륨 주의사항

MySQL 컨테이너가 처음 실행될 때 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DB_NAME`, `DB_USERNAME`을 사용해 초기 계정과 DB를 만든다.

이미 `mysql-data` 볼륨이 존재하면 `.env` 값을 바꿔도 기존 DB 계정 비밀번호는 자동으로 변경되지 않는다. 이 경우 `DB_PASSWORD`는 반드시 실제 MySQL `presentation` 계정 비밀번호와 같아야 한다.

데이터를 보존해야 하는 경우 `mysql-data` 볼륨을 삭제하지 않는다.

## 7. Dockerfile

| 파일 | 역할 |
|---|---|
| `infra/docker/frontend.Dockerfile` | Node 22로 Vite build 후 nginx static 제공 |
| `infra/docker/backend.Dockerfile` | Gradle `bootJar` 빌드 후 Java 21 JRE에서 실행, ffmpeg 설치 |
| `infra/docker/analysis-engine.Dockerfile` | Python 3.12, ffmpeg, OpenCV/MediaPipe 의존성 설치 |
| `infra/docker/video-llm-engine.Dockerfile` | Python 3.12, ffmpeg 설치 |
