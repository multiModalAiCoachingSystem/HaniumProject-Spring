# HTTPS 및 Reverse Proxy

## 환경 분리

개발 환경은 `infra/docker/docker-compose.yml`과 `infra/nginx/nginx.conf`를 사용하며 `http://localhost` 접속을 유지한다. HSTS와 Secure cookie를 강제하지 않는다.

운영 환경은 기본 Compose에 `infra/docker/docker-compose.prod.yml`을 함께 적용한다. `infra/nginx/nginx.prod.conf`가 80번 포트를 443으로 `308` redirect하고 TLS 1.2/1.3만 허용한다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  -f .\infra\docker\docker-compose.prod.yml `
  config
```

운영 `.env`에는 다음 값을 실제 환경에 맞게 설정한다.

```dotenv
APP_ENV=production
APP_BASE_URL=https://service.example
FRONTEND_ORIGINS=https://service.example
COOKIE_SECURE=true
COOKIE_SAME_SITE=Lax
TRUST_PROXY_HEADERS=true
TLS_CERTIFICATE_PATH=D:/external-certs/fullchain.pem
TLS_PRIVATE_KEY_PATH=D:/external-certs/privkey.pem
```

실제 도메인과 인증서는 저장소에 기록하지 않는다. 인증서와 개인키는 호스트의 저장소 외부 경로에서 Nginx 컨테이너의 `/etc/nginx/tls`로 read-only mount한다.

## Proxy Header

Nginx는 `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`를 backend에 전달한다. edge Nginx는 클라이언트가 보낸 `X-Forwarded-For`를 이어 붙이지 않고 실제 접속 주소로 덮어써 header 위조를 차단한다. backend의 rate limit은 기본적으로 servlet remote address만 사용하며 `TRUST_PROXY_HEADERS=true`일 때만 이 값을 사용한다. 이 설정은 backend 포트가 외부에 직접 공개되지 않고 신뢰하는 Nginx만 접근할 수 있을 때 켠다.

## 운영 보안 헤더

운영 Nginx는 HSTS, `nosniff`, frame 차단, referrer/permissions 정책, CSP를 추가한다. HSTS는 개발 HTTP 설정에는 없다. CSP는 현재 React 정적 리소스, 같은 origin API, `blob:` 영상 재생을 허용하는 최소 정책이다.

## 업로드와 Timeout

Nginx와 Spring Boot 모두 영상 크기를 500MB로 제한한다. Spring의 전체 multipart request 상한은 510MB다. Nginx는 업로드 request buffering을 끄고 API read/send timeout을 300초로 설정한다. WebSocket endpoint는 현재 없으므로 별도 upgrade 설정은 없다.
