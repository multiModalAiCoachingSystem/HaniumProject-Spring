# 시선

## 목적

발표자가 카메라 또는 청중 방향을 바라보는 비율을 추정한다. 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `gaze_analyzer.py`, `score_calculator.py`이다.

## 입력 데이터

- `eyeContactRatio`: 얼굴이 정면을 향한다고 판단된 face frame 비율
- `gazeAwayRatio`: `1.0 - eyeContactRatio`
- `faceDetectionRatio`: 얼굴 랜드마크가 감지된 프레임 비율

## 계산식

`_is_forward_gaze()`는 얼굴 랜드마크의 양눈과 코 위치를 사용한다.

```text
horizontal_offset = abs(nose.x - eye_center.x) / face_width
vertical_offset = abs(nose.y - eye_center.y) / face_width
forward = horizontal_offset <= 0.18 and 0.18 <= vertical_offset <= 0.72
eyeContactRatio = forward_gaze_frames / face_detected_frames
```

시선 점수:

```text
ratio >= 0.70: 92 + min(8, (ratio - 0.70) * 25)
0.40 <= ratio < 0.70: 70 + (ratio - 0.40) / 0.30 * 18
ratio < 0.40: 45 + ratio / 0.40 * 20
```

## 기준값

`eyeContactRatio < 0.7`이면 개선 피드백 사유가 생성된다.

## 점수화

시선 점수는 `EYE_CONTACT` category로 반환되며 최종 점수 가중치는 0.20이다.

## 예외 처리

얼굴 탐지가 실패하면 fallback으로 `eyeContactRatio=0.68` 또는 긴 발표의 경우 `0.62`를 사용한다.

## 한계

실제 청중을 바라보는지, 화면의 발표 자료를 보는지, 카메라 밖 사람을 보는지는 구분하지 못한다. 현재 구현은 프레임상 얼굴 방향 추정이다.
