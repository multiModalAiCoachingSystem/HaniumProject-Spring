# 통합 테스트

## 검증 범위

`Integration CI`는 매 실행마다 GitHub Actions의 새 MySQL 8.4 service container를 사용한다. 운영 DB, 개발자의 Docker volume, 운영 `.env`에는 접근하지 않는다.

검증 순서는 다음과 같다.

1. Gradle Wrapper로 Backend boot jar 생성
2. CI 전용 환경파일로 Compose 설정 검증
3. Backend Docker 이미지 빌드
4. 빈 MySQL에 Backend 연결
5. 시작 시 Flyway migration 및 JPA schema validate 수행
6. `/api/health` 응답 대기
7. `flyway_schema_history` 성공 row 확인
8. 회원가입과 로그인
9. Access Token으로 본인 정보와 리포트 목록 조회
10. 미인증 보호 API 차단 확인
11. 허용 Origin의 CORS preflight 확인
12. 로그인 Rate Limit의 `429` 응답 확인

## 테스트 데이터

- DB: 실행마다 폐기되는 `presentation_coaching_ci`
- 계정: CI 실행 중에만 생성되는 고정 테스트 계정
- 파일 저장소: `/tmp/presentation-coaching-ci-storage`
- JWT와 MySQL 비밀번호: 저장소에 공개 가능한 CI 전용 dummy 값

API smoke script는 토큰, 비밀번호, 응답 본문을 로그에 출력하지 않는다.

## 제외 범위

- 실제 영상 업로드와 장시간 분석
- NVIDIA NIM, Ollama, OpenAI 호출
- Whisper/Hugging Face 모델 다운로드
- Nginx TLS와 실제 외부 도메인
- 운영 백업 및 복구

외부 분석 엔진 URL은 연결되지 않는 loopback 주소로 설정한다. smoke test는 분석 작업을 생성하지 않으므로 외부 엔진 호출이 발생하지 않는다.

## 수동 실행

GitHub 저장소의 Actions 화면에서 `Integration CI`를 선택하고 `Run workflow`를 실행한다. 로컬에서는 Docker Compose 전체 기동 대신 서비스별 스크립트와 `validate-compose.ps1`을 먼저 사용한다.

통합 실패 시 `backend-integration-diagnostics` artifact와 `backend-ci.log`에서 Flyway, JPA validate, API 시작 실패 원인을 확인한다.

## 안정성 원칙

- MySQL health check 완료 후 Backend를 시작
- Backend health는 최대 90초만 polling
- 고정 CI DB를 실행마다 새 service container로 생성
- 테스트가 실제 시간, 사용자 영상, 외부 네트워크 응답에 의존하지 않음
- 백그라운드 Backend 프로세스는 성공과 실패 모두 종료

현재 알려진 flaky test는 없다. GitHub-hosted runner의 Docker image pull이나 패키지 저장소 장애는 테스트 실패와 구분해 Actions 로그에서 확인해야 한다.
