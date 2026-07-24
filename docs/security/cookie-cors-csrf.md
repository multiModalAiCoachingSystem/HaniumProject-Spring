# Cookie, CORS, CSRF 정책

## 현재 인증 구조

- access token은 로그인 응답 JSON으로 전달되고 frontend `localStorage`에 저장된다.
- frontend는 API 요청에 `Authorization: Bearer <token>`을 추가한다.
- refresh token은 DB에 SHA-256 hash로 저장하고 브라우저에는 `refreshToken` HttpOnly cookie로 전달한다.
- access token 저장 방식을 cookie로 전환하지 않았다.

## Refresh Cookie

| 속성 | 개발 | 운영 |
|---|---|---|
| HttpOnly | `true` | `true` |
| Secure | `false` | `true` |
| SameSite | `Lax` | `Lax` |
| Path | `/` | `/` |
| Max-Age | 14일 | 14일 |
| Domain | 미설정(host-only) | 미설정(host-only) |

`COOKIE_SAME_SITE=None`은 `COOKIE_SECURE=true`일 때만 시작할 수 있다. 운영에서 HTTP `APP_BASE_URL` 또는 Secure가 꺼진 cookie 설정을 사용하면 backend가 시작 단계에서 오류를 낸다.

## CORS

Spring Security가 `FRONTEND_ORIGINS`에 지정된 정확한 origin만 허용한다. 와일드카드와 빈 origin은 시작 시 거부한다.

- methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- headers: `Authorization`, `Content-Type`
- credentials: `true`
- preflight max age: 3600초

Nginx는 CORS 응답 헤더를 추가하지 않는다. 따라서 Spring과 Nginx의 중복 CORS 헤더가 없다.

## CSRF

일반 보호 API의 인증 근거는 브라우저가 자동 첨부하는 cookie가 아니라 JavaScript가 명시적으로 넣는 Bearer header다. 서버 세션도 사용하지 않으므로 Spring Security CSRF는 비활성화 상태를 유지한다.

refresh/logout은 HttpOnly cookie를 사용한다. 현재 방어선은 `SameSite=Lax`, 정확한 CORS origin, JSON API 정책이다. 운영 도메인에 신뢰할 수 없는 하위 도메인이 생기거나 cross-site cookie가 필요해 `SameSite=None`으로 바꾸는 경우에는 double-submit 또는 CSRF token 도입이 추가로 필요하다.

## 남은 위험

access token이 `localStorage`에 있으므로 XSS가 발생하면 탈취될 수 있다. 이번 변경은 인증 구조를 바꾸지 않았으며, 운영 CSP와 입력값 검증을 함께 유지해야 한다.
