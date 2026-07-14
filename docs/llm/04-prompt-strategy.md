# 프롬프트 전략

## Video LLM 프롬프트

현재 실제 모델 호출 프롬프트는 `video-llm-engine/app/model/qwen_vl_runner.py`의 `_prompt()`에서 문자열로 생성된다. `video-llm-engine/app/prompts/video_observation_prompt.md`는 placeholder 상태이며 실제 호출에 사용되지 않는다.

## 프롬프트 입력

프롬프트에는 다음 정보가 포함된다.

- 역할: `MulMoCo's Qwen-VL presentation coaching model`
- 지시: 샘플 프레임의 가시 증거만 근거로 분석
- 출력 제한: markdown 없이 valid JSON만 반환
- `Video metadata`
- `Sampled frame timestamps`
- JSON schema

## 프레임 입력

Ollama 요청의 `images`에는 샘플 프레임 중 최대 4개가 base64로 들어간다. 프레임 전체 목록은 최대 8개까지 추출하지만 모델 호출에는 앞 4개만 사용한다.

## 시간 구간 지시

모델은 샘플 프레임 timestamp만 사용하도록 지시받는다. 이슈가 보이는 프레임이 있으면 `startSeconds`를 해당 timestamp 근처로, `endSeconds`를 8~12초 뒤로 설정하도록 한다.

## Text LLM 프롬프트

현재 실행 경로에서는 `backend/src/main/java/com/hanium/presentation/application/feedback/TextFeedbackPromptBuilder.java`가 프롬프트를 생성한다. 별도 prompt 파일 로딩은 사용하지 않는다.

프롬프트에는 다음 규칙이 포함된다.

- 한국어로 작성
- JSON 객체만 반환
- Markdown 코드블록과 `<think>` 태그 금지
- 기존 점수 재계산 금지
- 실제 입력 데이터 기반 피드백 생성
- 과장된 표현 금지
- 발표자가 바로 실천할 수 있는 개선 방법 제시

입력 데이터에는 STT 대본, 발표 시간, `metrics`, `scores`, `totalScore`, analysis-engine observations, video-llm-engine observations, Video LLM metadata가 포함된다. STT 대본이 길면 앞부분과 끝부분을 보존하고 중간을 생략한다.

## 미구현

- 프롬프트 파일 기반 로딩
- 모델 응답을 엄격한 JSON Schema validator로 검증하는 단계
- 프롬프트 버전 관리
- OpenAI provider 실제 호출
