# 표정

## 목적

발표자의 얼굴 표정이 자연스럽게 유지되는지, 긴장된 표정이 과도하지 않은지, 적정 수준의 미소가 있는지를 평가한다. 현재 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `face_analyzer.py`, `score_calculator.py`이다.

## 입력 데이터

- `naturalExpressionRatio`: 긴장 표정으로 분류되지 않은 얼굴 검출 프레임 비율
- `tenseExpressionRatio`: 입 폭이 너무 좁거나 입 벌림이 큰 프레임 비율
- `smileRatio`: 입 폭과 입 벌림 기준으로 미소로 판단된 프레임 비율
- `faceDetectionRatio`: 얼굴 landmark가 검출된 프레임 비율
- `visionAnalysisFallback`: 영상/MediaPipe 분석 fallback 사용 여부

## landmark 사용 방식

MediaPipe FaceMesh landmark 중 얼굴 좌우, 입 좌우, 윗입술, 아랫입술 좌표를 사용한다.

```text
mouth_width = distance(left_mouth, right_mouth) / face_width
mouth_open = distance(upper_lip, lower_lip) / face_width

is_smile = mouth_width >= 0.36 and mouth_open <= 0.12
is_tense = mouth_width <= 0.25 or mouth_open >= 0.18
natural = not is_tense
```

전체 프레임에서 FaceMesh가 바로 실패하면 기존 crop retry 로직이 Haar face crop을 만든 뒤 FaceMesh를 다시 시도한다.

## 점수 계산식

표정 점수는 `ScoreCalculator.expression_score()`에서 계산한다.

```text
natural_score = 52 + clamp(naturalExpressionRatio, 0, 1) * 35
tension_penalty = min(35, clamp(tenseExpressionRatio, 0, 1) * 110)
smile_bonus = 적정 미소 구간 보너스
raw_score = natural_score - tension_penalty + smile_bonus
expressionScore = clamp(min(raw_score, confidence_cap), 0, 100)
```

`smile_bonus`는 미소가 많을수록 무조건 증가하지 않는다.

```text
smileRatio < 0.15          -> 0~2점
0.15 <= smileRatio <= 0.45 -> 2~5점
0.45 < smileRatio <= 0.70  -> 5점
smileRatio > 0.70          -> 5점에서 점진 감소, 최저 1점
```

## faceDetectionRatio 처리

얼굴 감지율이 낮으면 표정 점수의 신뢰도가 낮다고 보고 최고점을 제한한다.

```text
visionAnalysisFallback == true or faceDetectionRatio <= 0.0 -> 최대 66점
faceDetectionRatio < 0.05 -> 최대 82점
faceDetectionRatio < 0.20 -> 최대 88점
faceDetectionRatio < 0.50 -> 최대 94점
faceDetectionRatio >= 0.50 -> 최대 100점
```

## 100점 의미

100점은 얼굴 감지율이 충분하고, 자연스러운 표정 비율이 매우 높으며, 긴장 표정이 거의 없고, 미소가 적정 범위에 있을 때만 도달할 수 있다. 자연 표정 비율이 1.0이어도 얼굴 감지율이 낮거나 미소가 전혀 없으면 100점으로 포화되지 않는다.

## fallback

영상 파일 누락, OpenCV/MediaPipe 로딩 실패, 프레임 읽기 실패, 감지 가능한 프레임 부재 등에서는 vision fallback을 사용한다.

현재 fallback 기본값:

```text
naturalExpressionRatio = 0.74
tenseExpressionRatio = 0.12 또는 긴 발표에서 0.16
smileRatio = 0.18
faceDetectionRatio = 0.0
visionAnalysisFallback = true
```

fallback인 경우 `expressionScore`는 최대 66점으로 제한된다. fallback 값은 실제 얼굴 landmark 분석보다 유리한 최고점으로 처리하지 않는다.

## 한계

현재 표정 분석은 감정 인식 모델이 아니라 입 모양과 FaceMesh landmark 기반 휴리스틱이다. 얼굴이 너무 작거나 화면 밖으로 잘리면 감지율이 낮아지고, 표정 점수의 최고점이 제한된다. 장기 개선 항목은 더 강한 얼굴 detector 도입, MediaPipe Tasks Face Landmarker 전환, 얼굴 크기 촬영 가이드, 업로드 품질 경고이다.
