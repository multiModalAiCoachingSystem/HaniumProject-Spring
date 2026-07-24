# 시선

## 목적

발표자가 카메라 또는 청중 방향을 바라보는 비율을 추정한다. 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `gaze_analyzer.py`, `score_calculator.py`이다.

## 입력 데이터

- `eyeContactRatio`: 얼굴이 정면을 향한다고 판단된 face frame 비율
- `gazeAwayRatio`: `1.0 - eyeContactRatio`
- `faceDetectionRatio`: 얼굴 랜드마크가 감지된 프레임 비율
- `gazeDetectionRatio`: 시선 판정 가능한 FaceMesh landmark가 감지된 프레임 비율
- `gazeDetectedFrameCount`: 시선 판정 가능한 프레임 수

## 계산식

`_is_forward_gaze()`는 얼굴 랜드마크의 양눈과 코 위치를 사용한다.

```text
horizontal_offset = abs(nose.x - eye_center.x) / face_width
vertical_offset = abs(nose.y - eye_center.y) / face_width
forward = horizontal_offset <= 0.18 and 0.18 <= vertical_offset <= 0.72
eyeContactRatio = forward_gaze_frames / face_detected_frames
```

현재 시선 판정은 FaceMesh landmark가 감지된 프레임에서 수행하므로 `gazeDetectedFrameCount`는 `faceDetectedFrameCount`와 같고, `gazeDetectionRatio`는 `faceDetectionRatio`와 같다. 별도 eye detector를 사용하는 값으로 오해하지 않아야 한다.

시선 점수:

```text
ratio >= 0.70: 92 + min(8, (ratio - 0.70) * 25)
0.40 <= ratio < 0.70: 70 + (ratio - 0.40) / 0.30 * 18
ratio < 0.40: 45 + ratio / 0.40 * 20
```

위 식의 결과는 `eyeContactRawScore`로 보존한다. 최종 시선 점수는 얼굴·시선 검출 신뢰도에 따른 상한을 적용한다.

```text
fallback 또는 미검출 -> max 50
gazeDetectionRatio < 0.10 또는 gazeDetectedFrameCount < 10 -> max 60
0.10 <= gazeDetectionRatio < 0.30 -> max 70
0.30 <= gazeDetectionRatio < 0.50 -> max 85
gazeDetectionRatio >= 0.50 -> 정상 계산
```

`eyeContactRatio`가 높아도 검출률이 낮으면 일부 프레임만으로 전체 발표를 대표할 수 없으므로 최고점으로 표시하지 않는다. 응답에는 `gazeDetectionConfidence`, `gazeLowConfidence`, `gazeAnalysisReliable`, `gazeReliabilityLabel`, `gazeFallbackUsed`를 함께 기록한다.

## 기준값

`eyeContactRatio < 0.7`이면 개선 피드백 사유가 생성된다.

## 점수화

시선 점수는 `EYE_CONTACT` category로 반환되며 최종 점수 가중치는 0.20이다.

## 예외 처리

얼굴 탐지가 실패하면 fallback으로 `eyeContactRatio=0.68` 또는 긴 발표의 경우 `0.62`를 사용한다. 이 비율은 파이프라인 호환용 기본값이며 실제 검출 결과로 취급하지 않고 최종 점수는 최대 50점으로 제한한다.

## 한계

실제 청중을 바라보는지, 화면의 발표 자료를 보는지, 카메라 밖 사람을 보는지는 구분하지 못한다. 현재 구현은 프레임상 얼굴 방향 추정이다.
