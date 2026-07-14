# Text LLM 피드백 역할

## 현재 구현 상태

백엔드 분석 파이프라인은 `qwen3:4b` 기반 Text LLM 최종 피드백 생성을 수행한다. 호출 위치는 `AnalysisPipelineService.runAnalysis()`의 결과 병합 단계이며, `analysis-engine`과 `video-llm-engine` 결과를 받은 뒤 `TextFeedbackService`를 통해 최종 한국어 피드백을 생성한다.

Text LLM 호출이 실패해도 분석 작업은 `FAILED`가 되지 않는다. JSON 파싱 실패, Ollama 미실행, timeout, 빈 응답, provider 미지원 상황에서는 fallback 피드백을 생성해 `summary_json`에 저장한다.

## 관련 코드

| 파일 | 역할 |
|---|---|
| `backend/src/main/java/com/hanium/presentation/application/analysis/AnalysisPipelineService.java` | Text LLM 생성 결과를 최종 결과 저장 흐름에 병합 |
| `backend/src/main/java/com/hanium/presentation/application/feedback/TextFeedbackService.java` | provider 선택, Ollama 호출 결과 파싱, fallback 생성 |
| `backend/src/main/java/com/hanium/presentation/application/feedback/TextFeedbackPromptBuilder.java` | LLM 입력 데이터와 JSON 출력 지시 프롬프트 생성 |
| `backend/src/main/java/com/hanium/presentation/application/feedback/TextFeedbackResult.java` | 피드백 payload와 provider/model/fallback 메타데이터 |
| `backend/src/main/java/com/hanium/presentation/infrastructure/client/textllm/OllamaTextLlmClient.java` | Ollama `/api/generate` 호출 |
| `backend/src/main/java/com/hanium/presentation/global/properties/TextLlmProperties.java` | `app.text-llm` 설정 매핑 |

## 환경변수

| 환경변수 | Spring 설정 키 | 기본값 | 설명 |
|---|---|---|---|
| `TEXT_LLM_PROVIDER` | `app.text-llm.provider` | `OLLAMA` | `OLLAMA`, `NONE`, `OPENAI` 처리 |
| `TEXT_LLM_MODEL` | `app.text-llm.model` | `qwen3:4b` | Ollama Text LLM 모델명 |
| `OLLAMA_BASE_URL` | `app.text-llm.base-url` | `http://host.docker.internal:11434` | Ollama 서버 주소 |
| `TEXT_LLM_TIMEOUT_SECONDS` | `app.text-llm.timeout-seconds` | `30` | Ollama 응답 대기 제한 |

`OPENAI` provider는 현재 실제 호출이 미구현이다. `TEXT_LLM_PROVIDER=OPENAI`로 설정하면 fallback 피드백을 저장하며, API 키를 코드나 문서에 기록하지 않는다.

## 입력 데이터

Text LLM은 점수를 새로 계산하지 않고 다음 데이터를 설명 생성에만 사용한다.

- STT 대본
- 발표 시간
- `metrics`: 말하기 속도, Filler Word, 침묵, 자세, 시선, 표정, 제스처 관련 지표
- `scores`: 항목별 점수, 가중치, 등급, 요약
- `totalScore`
- analysis-engine observations
- video-llm-engine observations
- Video LLM provider와 metadata

STT 대본이 너무 길면 `TextFeedbackPromptBuilder`가 앞부분과 끝부분을 보존하고 중간을 생략해 최대 길이를 제한한다.

## 저장 위치

기존 DB 컬럼을 재사용한다.

- `feedback_json`: 기존 analysis-engine observation과 video-llm-engine observation 병합 결과 유지
- `summary_json.textFeedback`: Text LLM 최종 피드백 JSON
- `summary_json.textLlm`: provider, model, fallback 여부, fallback reason
- `scores_json`: 기존 점수 배열 유지

## 출력 구조

`summary_json.textFeedback`에는 다음 구조가 저장된다.

```json
{
  "presentationSummary": "발표 전체 요약",
  "overallFeedback": "종합 피드백",
  "strengths": [
    {
      "title": "강점 제목",
      "description": "설명",
      "relatedMetric": "EYE_CONTACT"
    }
  ],
  "weaknesses": [
    {
      "title": "개선 필요 항목",
      "description": "설명",
      "relatedMetric": "VOICE"
    }
  ],
  "improvementActions": [
    {
      "title": "개선 방법",
      "description": "실천 방법"
    }
  ],
  "expectedQuestions": [
    {
      "question": "예상 질문",
      "intent": "질문 의도",
      "suggestedAnswer": "답변 방향"
    }
  ]
}
```

## fallback 정책

다음 경우에는 `TextFeedbackService`가 fallback 피드백을 생성한다.

- `TEXT_LLM_PROVIDER=NONE`
- `TEXT_LLM_PROVIDER=OPENAI`
- 지원하지 않는 provider 값
- Ollama 서버 미실행
- 모델 미설치 또는 Ollama 오류
- 응답 timeout
- 빈 응답
- JSON 파싱 실패
- 프롬프트 생성 실패

fallback도 동일한 `textFeedback` 스키마를 사용하므로 프론트와 결과 조회 API 구조는 깨지지 않는다.
