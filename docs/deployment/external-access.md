# 외부 접속

## 현재 nginx 라우팅

`infra/nginx/nginx.conf` 기준:

- `/api/` -> `http://backend:8080`
- `/` -> `http://frontend:80`
- `client_max_body_size 500M`
- API 프록시는 request buffering off, read/send timeout 300초

## 컨테이너 내부 포트

| 서비스 | 내부 포트 | 외부 노출 |
|---|---:|---|
| nginx | 80 | `80:80` |
| frontend | 80 | compose에서는 nginx 뒤에 expose |
| backend | 8080 | 직접 ports 없음 |
| analysis-engine | 8001 | 직접 ports 없음 |
| video-llm-engine | 8002 | 직접 ports 없음 |
| mysql | 3306 | `3307:3306` |

## 개발 서버

프론트 개발 서버는 `frontend/vite.config.js`에서 5173 포트를 사용하고 `/api`를 `http://localhost:8080`으로 프록시한다.

## 운영 배포 시 확인할 것

- TLS 종료 지점
- `JWT_SECRET`과 DB 비밀번호 secret 처리
- 업로드 파일 크기 제한 500MB 유지 여부
- `HOST_STORAGE_PATH`의 실제 디스크 용량과 백업 정책
- Ollama 서버가 Docker 컨테이너에서 접근 가능한 주소인지 확인
