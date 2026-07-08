# Spring Boot 사용 이유

## 인증과 보안 흐름

현재 백엔드는 Spring Security 필터 체인으로 공개 API와 인증 API를 나눈다. `JwtAuthenticationFilter`가 `Authorization: Bearer` 토큰을 검증하고, `/api/admin/**`는 `ADMIN`, `MANAGER` 권한만 접근 가능하도록 설정한다.

## JPA 기반 상태 관리

`AnalysisJob`, `UploadedVideo`, `FinalAnalysisResult`, `User`, `RefreshToken`이 JPA 엔티티로 관리된다. 분석 작업 상태 전이는 엔티티 메서드 `processing()`, `done()`, `fail()`, `retry()`로 표현된다.

## 트랜잭션과 비동기 처리

`AnalysisCommandService.start()`는 job을 저장한 뒤 transaction commit 이후 비동기 분석을 시작한다. 이 구조는 DB에 없는 job을 비동기 작업이 참조하는 문제를 줄인다.

## 외부 엔진 연동

Spring WebClient로 `analysis-engine`과 `video-llm-engine`을 호출한다. 엔진 URL은 `ANALYSIS_ENGINE_BASE_URL`, `VIDEO_LLM_ENGINE_BASE_URL` 환경변수로 분리되어 있다.

## 운영 기능

운영 콘솔 API는 사용자 목록, 권한 변경, 사용자 추방, 분석 작업 조회/삭제를 제공한다. 추방 시 refresh token을 revoke해 기존 세션을 정리한다.

## 한계

현재 Text LLM 관련 클래스는 미구현이고, 분석 엔진 호출은 메시지 큐 없이 백엔드 비동기 스레드에서 직접 수행한다. 대규모 트래픽에서는 큐 기반 worker 분리가 추가로 필요하다.
