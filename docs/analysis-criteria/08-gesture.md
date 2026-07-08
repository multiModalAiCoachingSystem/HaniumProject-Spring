# 제스처

## 목적

손과 팔 움직임이 발표 내용 강조에 충분한지 평가한다. 현재 구현 위치는 `analysis-engine/app/services/basic_analyzer.py`의 `_gesture_metrics()`와 `score_calculator.py`의 `gesture_score()`이다.

## 입력 데이터

- `gestureMovementRatio`
- `gestureCountPerMinute`
- `stillHandRatio`
- `duration_seconds`

## 계산식

현재 손 랜드마크 기반 실제 제스처 분석은 미구현이다. `BasicAnalyzer._gesture_metrics()`가 임시 값을 반환한다.

```text
gestureMovementRatio = 0.46 if duration_seconds < 300 else 0.40
gestureCountPerMinute = 4.2
stillHandRatio = 0.18
```

점수 계산:

```text
movement_score = _range_score(movementRatio, 0.15, 0.30, 0.65, 0.85)
count_score = _range_score(countPerMinute, 1.0, 3.0, 8.0, 14.0)
still_penalty = min(18.0, stillHandRatio * 45.0)
gestureScore = clamp(average(movement_score, count_score) - still_penalty)
```

## 기준값

- 움직임 이상 구간: 0.30~0.65
- 분당 제스처 이상 구간: 3~8회
- `stillHandRatio > 0.1`: 손이 멈춰 있는 시간이 길다는 피드백

## 점수화

제스처 점수는 `GESTURE` category로 반환되며 최종 점수 가중치는 0.10이다.

## 예외 처리

현재는 실제 영상에서 손 탐지를 수행하지 않기 때문에 제스처 지표 자체의 분석 실패 예외가 없다.

## 한계

MediaPipe Hands 또는 pose 기반 팔 움직임 분석은 추가 예정이다. 현재 제스처 점수는 발표 길이에 따른 임시 추정값이므로 실제 손동작 품질을 정밀하게 반영하지 못한다.
