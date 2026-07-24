# Rate Limit

## 구현 방식

backend의 Spring Security filter chain에서 JWT 인증 필터 다음에 in-memory 고정 시간창 제한기를 실행한다. 제한을 초과한 요청은 multipart body가 Controller에서 처리되기 전에 차단된다.

| 분류 | 대상 | 기본 제한 |
|---|---|---|
| 로그인 | `POST /api/auth/login` | 분당 10회 |
| 회원가입 | `POST /api/auth/signup` | 시간당 5회 |
| 토큰 재발급 | `POST /api/auth/refresh` | 분당 20회 |
| 영상 업로드 | `POST /api/videos` | 시간당 20회 |
| 분석 시작 | `POST /api/analyses` | 시간당 20회 |
| 재시도/재분석 | `POST /api/analyses/{jobId}/retry` | 시간당 10회 |
| 관리자 API | `/api/admin/**` | 분당 60회 |
| 일반 API | 그 외 `/api/**` | 분당 120회 |

인증된 API는 principal별로, 로그인·회원가입·refresh는 client IP별로 bucket을 분리한다. proxy header는 `TRUST_PROXY_HEADERS=true`일 때만 사용한다. 로그에는 원래 IP나 계정값 대신 bucket key의 짧은 hash만 남긴다.

## 429 응답

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
  "retryAfterSeconds": 60
}
```

응답에는 `Retry-After` header도 포함된다. frontend는 429를 식별해 같은 안전한 사용자 메시지를 표시한다.

## 환경변수

제한값은 `RATE_LIMIT_LOGIN_PER_MINUTE`, `RATE_LIMIT_REGISTER_PER_HOUR`, `RATE_LIMIT_REFRESH_PER_MINUTE`, `RATE_LIMIT_UPLOAD_PER_HOUR`, `RATE_LIMIT_ANALYSIS_PER_HOUR`, `RATE_LIMIT_RETRY_PER_HOUR`, `RATE_LIMIT_ADMIN_PER_MINUTE`, `RATE_LIMIT_API_PER_MINUTE`로 변경한다.

## 한계와 확장 지점

현재 제한 상태는 backend 프로세스 메모리에만 있다. 재시작하면 초기화되며 backend 인스턴스가 여러 개면 각 인스턴스별 제한이 적용된다. 다중 인스턴스 운영 전에는 제한 저장소를 Redis 기반 분산 bucket으로 교체해야 한다.

로그인 bucket은 요청 body의 이메일을 읽지 않고 IP만 사용한다. 같은 NAT를 공유하는 사용자가 많으면 제한값 조정이 필요하다. 사용자당 동시 분석 개수 제한과 idempotency key는 현재 미구현이며 별도 정책 결정이 필요하다.
