# Text LLM 피드백 역할

## 현재 구현 상태

현재 프로젝트는 실제 OpenAI API를 사용하지 않는다. 설정은 `app.text-llm`과 Ollama `qwen3:4b`를 가리키지만, 백엔드의 텍스트 LLM 피드백 생성 흐름은 미구현이다.

요청에 따라 기존 `02-openai-feedback-role.md` 문서는 `02-text-llm-feedback-role.md`로 변경했다.

## 관련 코드

| 파일 | 상태 |
|---|---|
| `backend/src/main/resources/prompts/final-feedback-system-prompt.md` | 시스템 프롬프트 텍스트 존재 |
| `backend/src/main/java/.../global/properties/TextLlmProperties.java` | `provider`, `baseUrl`, `model` 설정 매핑 구현 |
| `backend/src/main/java/.../infrastructure/client/openai/OpenAiClient.java` | 빈 클래스, 미구현 |
| `OpenAiPromptBuilder.java`, `OpenAiResponseParser.java` | 빈 클래스, 미구현 |
| `FeedbackGenerateService.java`, `FeedbackFallbackService.java` | 빈 클래스, 미구현 |

## 환경변수

| 환경변수 | Spring 설정 키 | 기본값 |
|---|---|---|
| `TEXT_LLM_PROVIDER` | `app.text-llm.provider` | `OLLAMA` |
| `OLLAMA_BASE_URL` | `app.text-llm.base-url` | `http://host.docker.internal:11434` |
| `TEXT_LLM_MODEL` | `app.text-llm.model` | `qwen3:4b` |

## 기대 역할

추가 구현 시 Text LLM은 analysis-engine의 정량 점수와 video-llm-engine의 시각 관찰을 받아 한국어 최종 피드백을 생성하는 역할이 적합하다. 단, 현재는 `AnalysisPipelineService.saveEngineResult()`가 엔진 결과를 단순 병합하며 Text LLM 호출을 하지 않는다.

## 현재 출력

최종 피드백은 Text LLM이 아니라 다음 두 출처에서 생성된다.

- analysis-engine `ScoreCalculator.observations()`
- video-llm-engine `QwenVlRunner` 또는 mock observations

## 보안 주의

OpenAI API 키, Ollama 인증 토큰 같은 비밀정보 사용 코드는 현재 없다. 향후 외부 LLM API를 붙일 경우 키는 환경변수 또는 secret manager로만 주입해야 하며 문서에 실제 값을 기록하지 않는다.
