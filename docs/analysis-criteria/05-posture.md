# 자세

## 목적

어깨 수평, 고개 움직임, 상체 안정성을 바탕으로 발표 자세를 평가한다. 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `pose_analyzer.py`, `score_calculator.py`이다.

## 입력 데이터

- `shoulderTiltDegrees`: MediaPipe pose의 좌우 어깨 랜드마크 기울기 평균
- `headMovementRatio`: 코 랜드마크 이동량 평균을 0.08 기준으로 정규화한 값
- `postureStabilityRatio`: 어깨 기울기 10도 이하이고 움직임 0.035 이하인 pose 프레임 비율
- `poseDetectionRatio`, `visionFrameCount`, `visionAnalysisFallback`

## 계산식

```text
tilt_score:
  <= 5도: 100
  <= 10도: 85
  <= 20도: 70
  그 외: 50

movement_score = _linear_score(headMovementRatio, best=0.05, worst=0.35, reverse=True)
stability_score = postureStabilityRatio * 100
postureScore = average(tilt_score, movement_score, stability_score)
```

## 기준값

MediaPipe 분석은 최대 240프레임, 목표 2fps 수준으로 샘플링한다. 탐지 신뢰도는 pose/face 모두 `0.45`로 설정되어 있다.

## 점수화

자세 점수는 `POSTURE` category로 반환되며 최종 점수 가중치는 0.25이다.

## 예외 처리

영상 파일이 없거나 OpenCV/MediaPipe를 사용할 수 없거나 프레임을 열 수 없으면 fallback 지표를 사용한다.

- `shoulderTiltDegrees=6.0`
- `headMovementRatio=0.12`에 발표 길이에 따른 보정
- `postureStabilityRatio=0.80`에서 길이에 따른 보정
- `visionAnalysisFallback=True`

## 한계

카메라 각도, 앉은 자세, 상반신 일부만 보이는 영상에서는 어깨/코 랜드마크가 안정적으로 잡히지 않을 수 있다. 자세 교정의 의학적 판단은 제공하지 않는다.
