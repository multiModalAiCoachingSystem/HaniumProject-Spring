# Filler Word

## 목적

발표 중 불필요한 표현이 반복되는 정도를 평가한다. 현재 구현은 `analysis-engine/app/services/audio_analyzer.py`가 실제 사용 경로이며, `filler_analyzer.py`는 별도 클래스가 있으나 `BasicAnalyzer`에서 호출하지 않는다.

## 입력 데이터

- `transcript`: STT 결과 텍스트 또는 요청으로 전달된 외부 transcript
- `FILLERS`: `["음", "어", "그", "약간", "뭔가", "이제", "저", "그니까", "그러니까"]`

## 계산식

```text
fillerWordCount = sum(transcript.count(word) for word in FILLERS)
denominator = max(korean_syllable_count or token_count, 1)
fillerWordRatio = round(fillerWordCount / denominator, 4)
```

## 기준값

`ScoreCalculator.voice_score()`에서 `fillerWordRatio`가 패널티로 반영된다.

```text
filler_penalty = min(15.0, fillerWordRatio * 400.0)
```

## 점수화

필러 자체 점수 항목은 없다. `VOICE` 점수에서 최대 15점까지 감점한다.

## 예외 처리

transcript가 비어 있으면 `fillerWordRatio=0.015`를 기본값으로 사용한다. 이후 `VOICE` 점수 계산에서 기본 패널티가 적용된다.

## 한계

현재 구현은 단순 문자열 포함 횟수 기반이다. 예를 들어 `그`가 단어가 아니라 다른 단어의 일부로 등장해도 카운트될 수 있다. 형태소 분석, 문맥 기반 분류, 발화 습관별 예외 처리는 미구현이다.
