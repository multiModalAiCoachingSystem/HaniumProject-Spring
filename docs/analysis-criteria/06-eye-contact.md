# 시선

## 목적

발표자의 얼굴 방향과 눈동자 위치를 프레임 단위로 판정하여 카메라를 바라보는 비율을 계산한다. 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `gaze_analyzer.py`, `score_calculator.py`이다.

## 입력 데이터

- MediaPipe FaceMesh 얼굴 landmark
- 얼굴 방향 추정: 양쪽 바깥 눈꼬리 `33`, `263`과 코 `1`
- 눈동자 추정: `refine_landmarks=True`에서 생성되는 iris landmark `468~477`
- `faceAttemptedFrameCount`: 얼굴 분석을 시도한 프레임 수
- `gazeDetectedFrameCount`: 얼굴 방향 계산에 필요한 landmark geometry가 유효한 프레임 수
- `eyeContactFrameCount`: 정면 조건을 만족한 프레임 수

## 계산식

얼굴 방향은 양쪽 눈 사이 거리로 정규화한 코의 위치로 계산한다. 여기서 `yaw`, `pitch`는 `solvePnP`의 절대 Euler angle이 아니라 정면 기준으로 보정한 landmark 기반 편차 각도이다. `roll`은 현재 판정에 사용하지 않는다.

```text
horizontalOffset = (nose.x - eyeCenter.x) / outerEyeDistance
verticalOffset = (nose.y - eyeCenter.y) / outerEyeDistance
yawDegrees = degrees(atan(horizontalOffset))
pitchDegrees = degrees(atan(verticalOffset - 0.90))

headForward = abs(yawDegrees) <= 15 and abs(pitchDegrees) <= 10
irisForward = 0.32 <= irisHorizontalRatio <= 0.68
              and 0.20 <= irisVerticalRatio <= 0.80
lookingAtCamera = headForward and irisForward

eyeContactRatio = eyeContactFrameCount / gazeDetectedFrameCount
```

Iris 좌표를 사용할 수 없는 유효 FaceMesh 결과에서는 얼굴 방향 조건만 사용한다. Iris landmark가 잘못됐다는 이유만으로 얼굴 방향 판정 전체를 실패시키지 않는다. 동일한 정면 프레임이 반복되어도 각 프레임은 독립적으로 판정하며 움직임 여부를 유효 조건으로 사용하지 않는다.

## 기준값과 점수화

`eyeContactRatio`의 점수식은 `ScoreCalculator.eye_contact_score()`에 있다.

```text
ratio >= 0.70: 92 + min(8, (ratio - 0.70) * 25)
0.40 <= ratio < 0.70: 70 + (ratio - 0.40) / 0.30 * 18
ratio < 0.40: 45 + ratio / 0.40 * 20
```

계산 결과는 `eyeContactRawScore`로 보존되며, 얼굴 및 시선 검출 신뢰도에 따른 기존 상한이 최종 점수에 적용된다. 시선 점수의 최종 종합점수 가중치는 `EYE_CONTACT` 0.20이며 이번 gaze 판정 변경에서는 점수식과 가중치를 변경하지 않았다.

## 예외 처리와 fallback

- FaceMesh 결과가 없거나 필수 landmark geometry가 비정상이면 해당 프레임은 `gazeDetectedFrameCount`에 포함하지 않는다.
- 시선 판정 가능한 프레임이 하나도 없으면 기존 fallback `eyeContactRatio`를 사용한다.
- fallback 결과는 실제 검출 결과와 구분하며 기존 신뢰도 상한을 적용한다.
- 진단 로그에는 프레임 번호, 추정 yaw/pitch, iris 정규화 비율, 판정 결과만 남긴다. 얼굴 이미지, 전체 landmark 좌표, 영상 경로는 기록하지 않는다.

## 한계

현재 얼굴 방향 각도는 카메라 내부 파라미터를 사용하는 3D head-pose 절대각이 아니라 얼굴 landmark 비율에 기반한 근사값이다. 발표자가 카메라 옆 화면이나 청중을 바라보는 경우와 실제 렌즈 응시를 완전히 구분하지 못할 수 있으며, 안경 반사ㆍ눈 가림ㆍ매우 작은 얼굴에서는 iris 판정 신뢰도가 낮아질 수 있다.
