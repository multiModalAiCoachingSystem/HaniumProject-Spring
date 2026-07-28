# GitHub Actions CI

## 목적

CI는 운영 비밀정보나 실제 사용자 영상 없이 네 서비스의 테스트, 빌드, Docker Compose 설정, 빈 MySQL 데이터베이스의 Flyway 적용을 자동 검증한다.

## 워크플로

| 워크플로 | 트리거 | 검증 범위 |
|---|---|---|
| `CI` | Pull Request, `main`/`develop`/`development` push, 수동 실행 | Backend, Frontend, Analysis Engine, Video LLM Engine, Compose 설정 |
| `Integration CI` | `main` push, 수동 실행 | MySQL 빈 DB, Flyway, Backend 기동, API smoke test, Backend Docker 이미지 빌드 |

PR에서는 빠른 검증만 병렬로 실행한다. Docker 이미지 빌드와 실제 MySQL 기동은 `main` 또는 수동 통합 워크플로에서만 실행한다.

## 버전과 캐시

- Java: Temurin 21
- Node.js: 22 LTS
- Python: 3.12
- Gradle: 저장소의 Gradle Wrapper
- Gradle 캐시: `gradle/actions/setup-gradle`
- npm 캐시: `frontend/package-lock.json`
- pip 캐시: 각 엔진의 `requirements.txt`

빌드 산출물을 캐시로 재사용하지 않으며 `.env`, API Key, 토큰은 캐시에 포함하지 않는다.

## 외부 서비스 차단

- `TEXT_LLM_PROVIDER=NONE`
- `VIDEO_LLM_PROVIDER=NONE`
- `VIDEO_LLM_FALLBACK_PROVIDER=NONE`
- `NVIDIA_API_KEY`는 빈 값
- Ollama와 외부 엔진 URL은 연결되지 않는 loopback 포트를 사용
- Hugging Face 오프라인 환경변수를 설정해 테스트 중 모델 다운로드를 막음

Provider 단위 테스트는 fake runner 또는 mock client를 사용한다. 기본 CI는 NVIDIA NIM, Ollama, OpenAI API를 호출하지 않는다.

## 권한과 비밀정보

두 워크플로 모두 `contents: read`만 사용한다. 운영 GitHub Secret은 요구하지 않으며 fork Pull Request에서도 안전한 CI 전용 dummy 값만 사용한다.

CI 로그에는 Access Token, Authorization Header, DB dump, 사용자 영상, API 요청 본문을 출력하지 않는다.

## 실패 진단

- Backend 테스트 보고서: `backend-test-reports`, 7일 보관
- Backend 통합 로그와 테스트 보고서: `backend-integration-diagnostics`, 7일 보관
- Frontend 성공 빌드: `frontend-dist`, 3일 보관
- Python 테스트는 `unittest -v` 로그로 실패 테스트와 traceback을 확인
- Backend 기동 실패 또는 제한 시간 초과 시 `backend-ci.log`를 출력하고 업로드

각 job에는 5~25분 제한이 있고 health check는 제한 횟수 polling 후 실패한다. 백그라운드 프로세스는 `always()` 정리 단계 또는 shell trap으로 종료한다.

## Path Filter

현재는 공통 계약 변경을 놓치지 않도록 빠른 CI의 모든 job을 실행한다. 빌드 시간이 실제 병목으로 확인된 뒤에만 공통 파일과 Compose 변경의 의존성을 포함한 path filter를 도입한다.

## Gradle 경고

CI는 `./gradlew build --warning-mode all --no-daemon`을 별도 실행한다. 기존 Gradle 10 비호환 경고는 `io.spring.dependency-management` 1.1.6에서 발생했으며 1.1.7 패치 업데이트로 제거했다. 경고가 다시 발생하면 해당 step 로그와 Gradle problems report에서 plugin ID와 task를 확인한다.

현재 남은 Java 컴파일 경고는 `javax.annotation.meta.When` classpath와 `AnalysisPipelineServiceTest`의 unchecked operation이다. Gradle 10 제거 예정 기능 경고와는 별개이며 추후 테스트 타입 안전성 정리 대상으로 남긴다.

## 현재 테스트 한계

Frontend는 Node 내장 test runner로 날짜, 피드백 분류·중복 제거, 분석 신뢰도, Raw Metrics disclosure 같은 순수 로직을 검증한다. 브라우저 DOM과 네트워크 상호작용을 재현하는 환경은 아직 없어 로그인 오류, 업로드 진행률·취소, 분석 상태 버튼의 실제 클릭 흐름은 Backend 단위 테스트와 수동 통합 시나리오에 의존한다.

실제 분석 영상, 모델 다운로드, NVIDIA/Ollama 응답은 기본 CI에서 제외한다. 향후 비식별 synthetic fixture와 브라우저 component/E2E runner를 도입할 때에도 외부 모델은 stub server로 유지해야 한다.
