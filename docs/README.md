# Presentation Coaching AI 문서

현재 문서는 실제 코드와 설정을 기준으로 작성했다. 구현되지 않았거나 placeholder인 기능은 각 문서에 `미구현` 또는 `추가 예정`으로 표시했다.

## 문서 목록

| 분류 | 문서 | 목적 | 상태 |
|---|---|---|---|
| analysis-criteria | [전체 평가 점수](analysis-criteria/01-scoring-criteria.md) | 최종 점수 가중치와 산식 설명 | 작성 완료 |
| analysis-criteria | [말하기 속도](analysis-criteria/02-speech-speed.md) | `speechSpeedSpm` 계산과 기준 설명 | 작성 완료 |
| analysis-criteria | [Filler Word](analysis-criteria/03-filler-words.md) | 필러 카운트와 감점 설명 | 작성 완료 |
| analysis-criteria | [침묵](analysis-criteria/04-pause-silence.md) | pause 추정값과 한계 설명 | 작성 완료 |
| analysis-criteria | [자세](analysis-criteria/05-posture.md) | MediaPipe 자세 지표와 fallback 설명 | 작성 완료 |
| analysis-criteria | [시선](analysis-criteria/06-eye-contact.md) | 얼굴 방향 기반 시선 추정 설명 | 작성 완료 |
| analysis-criteria | [표정](analysis-criteria/07-facial-expression.md) | 입 모양 기반 표정 지표 설명 | 작성 완료 |
| analysis-criteria | [제스처](analysis-criteria/08-gesture.md) | 현재 임시 제스처 지표와 추가 예정 범위 설명 | 작성 완료 |
| api | [Backend API](api/backend-api.md) | Spring Boot API 명세, 인증, 소유권 검증 | 작성 완료 |
| api | [Analysis Engine API](api/analysis-engine-api.md) | `/api/basic-analysis` 명세 | 작성 완료 |
| api | [Video LLM Engine API](api/video-llm-engine-api.md) | `/api/video-llm-analysis` 명세 | 작성 완료 |
| api | [응답 형식](api/response-format.md) | 공통 응답과 오류 코드 | 작성 완료 |
| architecture | [전체 서비스 구성](architecture/01-system-overview.md) | 서비스 구성과 프론트 라우터 | 작성 완료 |
| architecture | [서버 분리 이유](architecture/02-server-separation.md) | 서버 분리 근거와 한계 | 작성 완료 |
| architecture | [분석 파이프라인](architecture/03-analysis-pipeline.md) | 비동기 분석 흐름과 단계 | 작성 완료 |
| architecture | [데이터 흐름](architecture/04-data-flow.md) | 업로드/분석/결과/삭제 흐름 | 작성 완료 |
| architecture | [실패 처리 및 재시도 흐름](architecture/05-error-handling.md) | 실패 메시지와 retry 상태 변경 | 작성 완료 |
| database | [ERD](database/erd.md) | 실제 JPA 엔티티 관계 | 작성 완료 |
| database | [테이블 정의](database/table-definition.md) | 테이블별 컬럼과 JSON/LONGTEXT | 작성 완료 |
| database | [상태값](database/status-model.md) | enum 상태와 재시도 컬럼 변경 | 작성 완료 |
| llm | [Video LLM 역할](llm/01-video-llm-role.md) | Qwen-VL/Ollama 영상 관찰 역할 | 작성 완료 |
| llm | [Text LLM 피드백 역할](llm/02-text-llm-feedback-role.md) | OpenAI 미사용 및 Text LLM 미구현 상태 | 작성 완료 |
| llm | [JSON 출력 스키마](llm/03-json-schema.md) | 엔진/백엔드 JSON 구조 | 작성 완료 |
| llm | [프롬프트 전략](llm/04-prompt-strategy.md) | 현재 사용 프롬프트와 미사용 파일 | 작성 완료 |
| llm | [Fallback 전략](llm/05-fallback-strategy.md) | STT, MediaPipe, Qwen-VL fallback | 작성 완료 |
| presentation | [프로젝트 소개](presentation/project-introduction.md) | 심사위원용 프로젝트 개요 | 작성 완료 |
| presentation | [아키텍처 선택 비교](presentation/architecture-comparison.md) | 구조 선택 근거 비교 | 작성 완료 |
| presentation | [Spring Boot 사용 이유](presentation/spring-boot-advantages.md) | 백엔드 기술 선택 이유 | 작성 완료 |
| presentation | [최종 시연 시나리오](presentation/final-demo-scenario.md) | 사용자/운영자 시연 흐름 | 작성 완료 |
| deployment | [Docker 실행 가이드](deployment/docker-guide.md) | compose와 Dockerfile 설명 | 작성 완료 |
| deployment | [환경변수](deployment/environment-variables.md) | 환경변수 이름과 사용 위치 | 작성 완료 |
| deployment | [외부 접속](deployment/external-access.md) | nginx, 포트, 외부 접근 구조 | 작성 완료 |
| security | [비밀정보 관리](security/secret-management.md) | secret 처리 원칙과 주의점 | 작성 완료 |
| testing | [테스트 시나리오](testing/test-scenarios.md) | 자동/수동 테스트 기준 | 작성 완료 |
| testing | [실제 영상 시각 분석 회귀 테스트](testing/vision-regression-test.md) | 얼굴·시선·제스처 실영상 기준값과 알려진 한계 | 작성 완료 |
| troubleshooting | [자주 발생하는 오류](troubleshooting/common-errors.md) | 운영 중 흔한 문제와 확인 위치 | 작성 완료 |

## 작성 기준

- 클래스명, API 경로, enum 값, 환경변수명은 현재 코드 기준으로 작성했다.
- 실제 비밀번호, JWT secret, token, API key 값은 기록하지 않았다.
- 현재 미구현인 Text LLM, OpenAI 호출, 실제 제스처 분석, 분석 취소, 운영 로그 저장은 구현된 것처럼 서술하지 않았다.
