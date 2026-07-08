# 말하기 속도

## 목적

발표자가 너무 빠르거나 느리게 말하는지 평가한다. 현재 구현 위치는 `analysis-engine/app/services/audio_analyzer.py`와 `analysis-engine/app/services/score_calculator.py`의 `voice_score`이다.

## 입력 데이터

- `duration_seconds`: `VideoInfoService.probe()`가 읽은 영상 길이
- `transcript`: STT 결과 또는 요청으로 전달된 외부 transcript
- `speechSpeedSpm`: `AudioAnalyzer.analyze()`가 계산한 분당 음절 수 또는 토큰 수

## 계산식

한국어 음절 수가 있으면 한글 음절 수를 사용하고, 없으면 공백 기준 토큰 수를 사용한다.

```text
speech_units = korean_syllable_count or token_count
speechSpeedSpm = round(speech_units / max(duration_seconds / 60, 0.1), 1)
```

말하기 속도 점수는 `ScoreCalculator._range_score`를 사용한다.

```text
lower=220, ideal_low=260, ideal_high=330, upper=380
```

## 기준값

- 260~330 `speechSpeedSpm`: 이상 구간, 100점
- 220~260: 65점에서 100점까지 선형 증가
- 330~380: 100점에서 65점까지 선형 감소
- 220 미만 또는 380 초과: 55점

## 점수화

말하기 속도는 `VOICE` 항목의 일부로만 반영된다. `VOICE` 점수는 속도 점수와 음량 안정성 점수 평균에서 침묵/필러 패널티를 뺀 값이다.

## 예외 처리

transcript가 비어 있으면 `AudioAnalyzer`는 `speechSpeedSpm=285.0`을 기본값으로 사용한다. STT가 꺼져 있거나 실패해도 분석 전체가 바로 실패하지 않고 기본 음성 지표로 계속 진행된다.

## 한계

현재 속도는 실제 발화 구간만이 아니라 영상 전체 길이를 분모로 사용한다. 긴 무음 구간이 있는 영상은 실제 말하기 속도보다 낮게 계산될 수 있다.
