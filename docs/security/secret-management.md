# 비밀정보 관리

## 원칙

비밀번호, JWT 서명 키, API 키, 토큰은 코드와 문서에 실제 값으로 기록하지 않는다. 이 문서는 변수명과 사용 위치만 설명한다.

## 현재 비밀정보 사용 위치

| 항목 | 환경변수 | 사용 위치 |
|---|---|---|
| DB 비밀번호 | `DB_PASSWORD`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | `application.yml`, `docker-compose.yml` |
| JWT 서명 키 | `JWT_SECRET` | `JwtTokenProvider` |
| refresh token | cookie `refreshToken` | `AuthController`, `AuthService` |

## 저장 방식

- access token: 프론트 `localStorage.accessToken`
- refresh token: httpOnly cookie 이름 `refreshToken`
- refresh token DB 저장: 원문이 아니라 SHA-256 해시인 `token_hash`
- 비밀번호: BCrypt hash

## 현재 보안상 주의할 점

- 개발 설정 파일과 compose에 개발용 평문 기본값이 존재한다. 운영에서는 반드시 외부 secret으로 대체해야 한다.
- `JwtTokenProvider`의 기본 secret은 운영에 사용하면 안 된다.
- refresh cookie는 `HttpOnly`와 `Path=/`만 설정되어 있고, `Secure`, `SameSite` 설정은 현재 코드에 없다.
- CSRF는 disable되어 있다. 현재는 stateless JWT 구조지만 refresh cookie를 사용하므로 운영 정책 검토가 필요하다.

## 문서에서 제외한 정보

실제 DB 비밀번호, JWT secret, refresh token, access token, API 키는 기록하지 않는다.
