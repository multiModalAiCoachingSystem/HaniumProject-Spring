# Presentation Coaching AI 문서

이 디렉터리는 실제 프로젝트 코드와 운영 설정을 설명한다. 비밀값과 사용자 데이터는 포함하지 않으며, 미구현 항목은 완료 기능처럼 기록하지 않는다.

| 분류 | 문서 | 목적 | 상태 |
|---|---|---|---|
| analysis-criteria | [전체 평가 점수](analysis-criteria/01-scoring-criteria.md) | 최종 점수 계산 기준 | 작성 완료 |
| analysis-criteria | [말하기 속도](analysis-criteria/02-speech-speed.md) | 발화 속도 측정 및 점수화 | 작성 완료 |
| analysis-criteria | [Filler Word](analysis-criteria/03-filler-words.md) | 불필요 표현 측정 및 점수화 | 작성 완료 |
| analysis-criteria | [침묵](analysis-criteria/04-pause-silence.md) | pause 측정과 예외 처리 | 작성 완료 |
| analysis-criteria | [자세](analysis-criteria/05-posture.md) | 자세 지표와 fallback | 작성 완료 |
| analysis-criteria | [시선](analysis-criteria/06-eye-contact.md) | 시선 지표와 신뢰도 | 작성 완료 |
| analysis-criteria | [표정](analysis-criteria/07-facial-expression.md) | 표정 지표와 신뢰도 | 작성 완료 |
| analysis-criteria | [제스처](analysis-criteria/08-gesture.md) | Pose 기반 제스처 지표 | 작성 완료 |
| api | [Backend API](api/backend-api.md) | 인증, 분석, 리포트 API | 작성 완료 |
| api | [Analysis Engine API](api/analysis-engine-api.md) | 정량 분석 엔진 API | 작성 완료 |
| api | [Video LLM Engine API](api/video-llm-engine-api.md) | Video LLM 분석 API | 작성 완료 |
| api | [응답 형식](api/response-format.md) | 공통 응답과 오류 형식 | 작성 완료 |
| architecture | [전체 서비스 구성](architecture/01-system-overview.md) | 서비스와 인프라 구성 | 작성 완료 |
| architecture | [서버 분리 이유](architecture/02-server-separation.md) | Spring Boot와 분석 엔진 분리 근거 | 작성 완료 |
| architecture | [분석 파이프라인](architecture/03-analysis-pipeline.md) | 비동기 분석 단계 | 작성 완료 |
| architecture | [데이터 흐름](architecture/04-data-flow.md) | 업로드부터 결과 조회까지의 흐름 | 작성 완료 |
| architecture | [실패 처리](architecture/05-error-handling.md) | 취소, 실패, 재시도 흐름 | 작성 완료 |
| database | [ERD](database/erd.md) | Entity 관계 | 작성 완료 |
| database | [테이블 정의](database/table-definition.md) | 컬럼, FK, LONGTEXT 정의 | 작성 완료 |
| database | [상태 모델](database/status-model.md) | 분석 상태와 전이 | 작성 완료 |
| database | [Flyway 마이그레이션](database/flyway-migrations.md) | baseline, migration, checksum 운영 | 작성 완료 |
| llm | [Video LLM 역할](llm/01-video-llm-role.md) | NVIDIA/Ollama 시각 관찰 역할 | 작성 완료 |
| llm | [Text LLM 피드백 역할](llm/02-text-llm-feedback-role.md) | qwen3 한국어 피드백 역할 | 작성 완료 |
| llm | [JSON 스키마](llm/03-json-schema.md) | LLM 입출력 JSON 구조 | 작성 완료 |
| llm | [프롬프트 전략](llm/04-prompt-strategy.md) | 프롬프트 구성 원칙 | 작성 완료 |
| llm | [Fallback 전략](llm/05-fallback-strategy.md) | Provider 및 파싱 실패 대응 | 작성 완료 |
| presentation | [프로젝트 소개](presentation/project-introduction.md) | 발표용 프로젝트 개요 | 작성 완료 |
| presentation | [아키텍처 비교](presentation/architecture-comparison.md) | 기술 선택 비교 | 작성 완료 |
| presentation | [Spring Boot 사용 이유](presentation/spring-boot-advantages.md) | backend 기술 선택 근거 | 작성 완료 |
| presentation | [최종 시연](presentation/final-demo-scenario.md) | 사용자 시연 순서 | 작성 완료 |
| deployment | [Docker 실행](deployment/docker-guide.md) | Compose 실행과 mount 확인 | 작성 완료 |
| deployment | [환경변수](deployment/environment-variables.md) | 환경변수명과 사용 위치 | 작성 완료 |
| deployment | [외부 접속](deployment/external-access.md) | Nginx와 외부 접근 | 작성 완료 |
| deployment | [백업·복원](deployment/backup-and-restore.md) | DB/storage 백업, checksum, 복원 훈련 | 작성 완료 |
| security | [비밀정보 관리](security/secret-management.md) | secret 보관 원칙 | 작성 완료 |
| security | [HTTPS와 Proxy](security/https-and-proxy.md) | TLS와 proxy header 정책 | 작성 완료 |
| security | [Cookie, CORS, CSRF](security/cookie-cors-csrf.md) | 브라우저 인증 보안 | 작성 완료 |
| security | [Rate Limit](security/rate-limiting.md) | endpoint별 요청 제한 | 작성 완료 |
| testing | [테스트 시나리오](testing/test-scenarios.md) | 자동·수동 회귀 기준 | 작성 완료 |
| testing | [시각 분석 회귀](testing/vision-regression-test.md) | 얼굴·시선·제스처 검증 기준 | 작성 완료 |
| troubleshooting | [공통 오류](troubleshooting/common-errors.md) | 서비스별 장애 확인 | 작성 완료 |
| troubleshooting | [DB 복구 오류](troubleshooting/database-recovery.md) | Flyway, dump, storage 복구 장애 대응 | 작성 완료 |

## 작성 원칙

- 클래스명, API 경로, 환경변수, enum은 실제 코드와 일치시킨다.
- 실제 비밀번호, JWT secret, token, API Key, 사용자 데이터는 기록하지 않는다.
- 계산 기준과 변경 가능한 수치는 코드 또는 설정 위치를 함께 적는다.
- 구현되지 않은 기능은 `미구현` 또는 `추가 예정`으로 표시한다.
- 이미 적용된 Flyway migration 파일은 수정하지 않고 새 버전을 추가한다.

## 테스트와 CI 문서

| 문서 | 목적 | 작성 상태 |
|---|---|---|
| [GitHub Actions CI](testing/ci.md) | PR 및 main 자동 검증 구조, 캐시, 권한, artifact 정책 | 작성 완료 |
| [로컬 테스트 가이드](testing/local-test-guide.md) | 프로젝트 루트 및 서비스별 반복 실행 명령 | 작성 완료 |
| [통합 테스트](testing/integration-tests.md) | 빈 MySQL, Flyway, Backend API smoke test 범위 | 작성 완료 |
