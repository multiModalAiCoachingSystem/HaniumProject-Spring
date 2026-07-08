# 침묵

## 목적

발표 흐름이 중간 침묵 때문에 끊기는 정도를 `VOICE` 점수에 반영한다. 현재 구현은 `analysis-engine/app/services/audio_analyzer.py`와 `score_calculator.py`에 있다.

## 입력 데이터

- `duration_seconds`: 영상 길이
- `pauseCount`: `AudioAnalyzer`가 추정한 침묵 횟수
- `pauseRatio`: `AudioAnalyzer`가 반환한 침묵 비율

## 계산식

현재 `pauseCount`와 `pauseRatio`는 실제 음성 에너지 분석이 아니라 고정/추정값이다.

```text
pauseCount = max(1, int(duration_seconds // 35))
pauseRatio = 0.08
pause_penalty = min(18.0, pauseRatio * 120.0)
```

## 기준값

`pauseRatio > 0.05`이면 관찰 피드백에서 "중간 침묵이 많다"는 개선 사유가 생성된다.

## 점수화

침묵은 독립 점수가 아니라 `VOICE` 점수에서 최대 18점 감점으로 반영된다.

## 예외 처리

영상 길이를 읽지 못하면 `VideoInfoService`가 기본 길이 `180.0`초를 사용할 수 있다. 이 경우 `pauseCount`도 기본 길이를 기준으로 추정된다.

## 한계

무음 구간 검출, VAD 기반 pause segmentation, 구간별 침묵 타임라인은 미구현이다. 현재 수치는 발표 길이에 기반한 보수적 추정값이다.
