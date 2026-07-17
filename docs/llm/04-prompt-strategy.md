# 프롬프트 전략

## Video LLM 프롬프트

Video LLM 프롬프트는 provider별 runner에서 생성한다.

| Provider | 구현 위치 | 역할 |
|---|---|---|
| `NVIDIA_NIM` | `video-llm-engine/app/model/nvidia_nemotron_runner.py` | Nemotron에 영어 시각 관찰 JSON을 요청 |
| `OLLAMA` | `video-llm-engine/app/model/qwen_vl_runner.py` | 기존 Qwen-VL에 프레임 관찰 JSON을 요청 |

## NVIDIA Nemotron 프롬프트

Nemotron system prompt에는 다음 규칙이 포함된다.

- valid JSON만 반환
- Markdown code fence 금지
- 직접 관찰 가능한 시각 행동만 설명
- STT, 말하기 속도, 침묵, Filler Word, 최종 점수 추론 금지
- identity 또는 민감한 개인 속성 추론 금지
- 긴장감이나 자신감은 단정하지 않고 조심스럽게 표현

user prompt에는 `Video metadata`, 샘플 프레임 timestamp, 반환 JSON schema가 포함된다. 이미지 입력은 OpenAI-compatible content 배열의 `image_url` data URL로 전달한다.

Nemotron은 영어 시각 관찰 결과를 생성한다. 최종 한국어 피드백은 backend의 Text LLM `qwen3:4b`가 담당한다.

## Ollama Qwen-VL 프롬프트

`QwenVlRunner._prompt()`는 기존 qwen3-vl 경로에서 사용된다. 프롬프트에는 다음 정보가 포함된다.

- 역할: `MulMoCo's Qwen-VL presentation coaching model`
- 지시: 샘플 프레임의 가시 증거만 근거로 분석
- 출력 제한: markdown 없이 valid JSON만 반환
- `Video metadata`
- `Sampled frame timestamps`
- JSON schema

## 프레임 입력

NVIDIA 요청에는 기본 8개 프레임이 `image_url` data URL로 들어간다. `NVIDIA_MAX_IMAGES`로 조정할 수 있지만 NVIDIA API의 이미지 수 제한을 고려해 내부 상한은 10개다.

Ollama 요청의 `images`에는 샘플 프레임 중 최대 4개가 base64로 들어간다. 프레임 전체 목록은 기본 8개까지 추출하고, 외부에서 큰 값이 들어와도 `VIDEO_LLM_MAX_FRAMES=16`으로 제한한다.

원본 500MB 영상 전체를 `video_url` base64로 전송하는 기능은 구현하지 않았다.

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
