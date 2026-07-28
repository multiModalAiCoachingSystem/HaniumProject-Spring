# 제스처

## 목적

발표 영상에서 팔과 손목의 움직임 양, 빈도, 좌우 균형과 안정성을 계산한다. 구현 위치는 `analysis-engine/app/services/gesture_analyzer.py`, `basic_analyzer.py`, `score_calculator.py`다.

## 입력과 landmark

`GestureAnalyzer`는 Video LLM용 저장 프레임을 사용하지 않고 OpenCV로 영상을 직접 읽으며 최대 240프레임을 분석한다. MediaPipe Pose index는 다음과 같다.

| landmark | index |
|---|---:|
| left/right shoulder | 11 / 12 |
| left/right elbow | 13 / 14 |
| left/right wrist | 15 / 16 |

visibility 0.15 이상이고 정규화 좌표가 허용 범위인 landmark만 사용한다. 손목 이동 65%와 팔꿈치 이동 35%를 어깨 너비로 정규화한다.

## 실제 지표

- `gestureAverageMovement`: 유효 프레임 간 평균 정규화 이동량
- `gestureMovementRatio`: 평균 이동량을 0.16 기준으로 0~1 정규화
- `gestureFrequency`, `gestureCountPerMinute`: 이동량 0.06 이상인 구간의 분당 빈도
- `gestureBalance`: 좌우 누적 이동량의 균형, 0~1
- `gestureStability`: 비정상 이동 outlier 비율의 역값
- `gestureVisibleRatio`: 유효 팔 landmark 프레임 / 샘플 프레임
- `stillHandRatio`: 이동량 0.015 미만 비율
- `gestureActivityScore`: 활동량, 빈도, 균형, 안정성의 0~100 점수

## 활동 점수

```text
movementScore  = 적정 movementRatio 0.35~0.68에서 최고
frequencyScore = 적정 frequency 3~9회/분에서 최고
balanceScore   = 55 + gestureBalance * 45
stabilityScore = 45 + gestureStability * 55

gestureActivityScore =
  movementScore * 0.40 +
  frequencyScore * 0.30 +
  balanceScore * 0.15 +
  stabilityScore * 0.15
```

움직임이나 빈도가 적정 구간을 지나 과도해지면 점수가 낮아진다. 움직임이 많을수록 무조건 유리하지 않다.

## 최종 제스처 점수

```text
movementScore = rangeScore(gestureMovementRatio, 0.15, 0.30, 0.65, 0.85)
countScore = rangeScore(gestureCountPerMinute, 1, 3, 8, 14)
stillPenalty = min(18, stillHandRatio * 45)
baseScore = average(movementScore, countScore) - stillPenalty
gestureScore = clamp(baseScore * 0.55 + gestureActivityScore * 0.45)
```

최종 종합점수 가중치는 10%다.

## fallback

다음이면 결정적 fallback을 사용하고 전체 분석은 계속한다.

- 원본 영상 누락 또는 열기 실패
- OpenCV/MediaPipe 로딩 실패
- frame count 확인 실패
- 샘플 또는 유효 프레임이 8개 미만
- `gestureVisibleRatio < 0.20`
- 유효한 프레임 간 이동량 없음
- 처리 중 예외

fallback은 호환용 `gestureMovementRatio=0.46`, `gestureCountPerMinute=4.2`, `stillHandRatio=0.18`, `gestureActivityScore=72.0`을 사용하고 `gestureAnalysisFallback=true`와 사유를 기록한다. 실제 영상 기반 값으로 오해하지 않아야 한다.

## 한계

MediaPipe Hands가 아니라 Pose 팔 좌표를 사용하므로 손가락 모양과 작은 손동작은 분석하지 않는다. 팔이 화면 밖에 있거나 발표대에 가려지면 감지율과 정확도가 낮아진다.
