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

`backend/src/main/resources/prompts/final-feedback-system-prompt.md`는 존재하지만 현재 실행 경로에서 사용되지 않는다. 내용은 정량 점수를 변경하지 말고 구조화 데이터만 근거로 한국어 피드백을 JSON Schema에 맞게 작성하라는 역할 지시이다.

## 미구현

- 프롬프트 파일 기반 로딩
- Text LLM 최종 피드백 생성
- 모델 응답을 엄격한 JSON Schema validator로 검증하는 단계
- 프롬프트 버전 관리
