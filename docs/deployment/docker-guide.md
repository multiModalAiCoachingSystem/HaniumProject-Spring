# Docker 실행 가이드

## 구성 파일

Docker Compose 파일은 `infra/docker/docker-compose.yml`이다. 서비스는 `mysql`, `analysis-engine`, `video-llm-engine`, `backend`, `frontend`, `nginx`로 구성된다.

## 실행 개요

```powershell
cd infra/docker
docker compose up --build
```

브라우저 접속은 nginx 기준 `http://localhost`이다. backend는 컨테이너 내부에서 8080, analysis-engine은 8001, video-llm-engine은 8002를 사용한다.

## 볼륨

- MySQL 데이터: `mysql-data`
- 영상/분석 파일: `${HOST_STORAGE_PATH:-D:/presentation-coaching-ai/storage}:/data`

## Dockerfile

| 파일 | 역할 |
|---|---|
| `infra/docker/frontend.Dockerfile` | Node 22로 Vite build 후 nginx static 제공 |
| `infra/docker/backend.Dockerfile` | Gradle bootJar 빌드 후 Java 21 JRE에서 실행, ffmpeg 설치 |
| `infra/docker/analysis-engine.Dockerfile` | Python 3.12, ffmpeg, OpenCV/MediaPipe 의존성 설치 |
| `infra/docker/video-llm-engine.Dockerfile` | Python 3.12, ffmpeg 설치 |

## 주의

Compose 파일에는 개발용 기본값이 포함되어 있다. 운영 배포에서는 DB 비밀번호와 `JWT_SECRET`을 반드시 외부 secret 또는 환경변수로 바꿔야 하며, 문서나 저장소에 실제 값을 기록하지 않는다.
