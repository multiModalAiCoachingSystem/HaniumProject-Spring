# 표정

## 목적

발표자의 얼굴 표정이 자연스럽게 유지되는지, 긴장 표정이 과도하지 않은지, 적절한 미소가 있는지를 평가한다. 현재 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `analysis-engine/app/services/face_analyzer.py`, `analysis-engine/app/services/score_calculator.py`이다.

## 입력 데이터

- `naturalExpressionRatio`: 긴장 표정으로 분류되지 않은 얼굴 검출 프레임 비율
- `tenseExpressionRatio`: 입 폭이 너무 좁거나 입 벌림이 큰 프레임 비율
- `smileRatio`: 입 폭과 입 벌림 기준으로 미소로 판단된 프레임 비율
- `faceDetectionRatio`: 얼굴 landmark가 검출된 프레임 비율, 0~1 범위
- `faceDetectedFrameCount`: 얼굴 landmark가 검출된 프레임 수
- `faceAttemptedFrameCount`: 얼굴 검출을 시도한 프레임 수
- `visionAnalysisFallback`: 영상/MediaPipe 분석 fallback 사용 여부

## Landmark 사용 방식

MediaPipe FaceMesh landmark 중 얼굴 좌우, 입 좌우, 윗입술, 아랫입술 좌표를 사용한다.

```text
mouth_width = distance(left_mouth, right_mouth) / face_width
mouth_open = distance(upper_lip, lower_lip) / face_width

is_smile = mouth_width >= 0.36 and mouth_open <= 0.12
is_tense = mouth_width <= 0.25 or mouth_open >= 0.18
natural = not is_tense
```

전체 프레임에서 FaceMesh가 바로 실패하면 기존 crop retry 로직이 Haar face crop을 만든 뒤 FaceMesh를 다시 시도한다.

## 점수 계산

표정 점수는 `ScoreCalculator.expression_score()`에서 계산한다.

```text
natural_score = 52 + clamp(naturalExpressionRatio, 0, 1) * 35
tension_penalty = min(35, clamp(tenseExpressionRatio, 0, 1) * 110)
smile_bonus = limited_smile_bonus(smileRatio)
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

## Face Detection 신뢰도 처리

얼굴 감지율이 낮으면 표정 점수의 신뢰도가 낮다고 보고 최고점을 제한한다. 감지된 일부 프레임이 모두 자연스러워도 전체 영상의 표정이 충분히 분석된 것으로 보지 않는다.

```text
visionAnalysisFallback == true or faceDetectionRatio <= 0.0 -> max 50
faceDetectionRatio < 0.10 or faceDetectedFrameCount < 10 -> max 60
faceDetectionRatio < 0.30 -> max 70
faceDetectionRatio < 0.50 -> max 85
faceDetectionRatio >= 0.50 -> max 100
```

`faceDetectionRatio < 0.10` 또는 `faceDetectedFrameCount < 10`이면 다음 필드를 함께 기록한다.

```text
expressionLowConfidence = true
expressionAnalysisReliable = false
expressionConfidenceReason = low_face_detection 또는 very_low_face_detection
expressionDetectionConfidence = 0.0~1.0
expressionReliabilityLabel = 분석 불가 또는 감지 신뢰도 매우 낮음/낮음/제한됨/정상
expressionFallbackUsed = true 또는 false
```

## 100점 의미

100점은 얼굴 감지율이 충분하고, 자연스러운 표정 비율이 높으며, 긴장 표정이 거의 없고, 미소가 적정 범위에 있을 때만 가능하다. `naturalExpressionRatio`가 1.0이어도 얼굴 감지율이나 검출 프레임 수가 부족하면 100점으로 포화되지 않는다.

## Fallback

영상 파일 누락, OpenCV/MediaPipe 로딩 실패, 프레임 읽기 실패, 감지 가능한 프레임 부족 등의 경우 vision fallback을 사용한다.

현재 fallback 기본값:

```text
naturalExpressionRatio = 0.74
tenseExpressionRatio = 0.12 또는 긴 발표에서 0.16
smileRatio = 0.18
faceDetectionRatio = 0.0
visionAnalysisFallback = true
```

fallback인 경우 `expressionScore`는 최대 50점으로 제한된다. fallback 값은 실제 얼굴 landmark 분석보다 유리한 정상 점수로 처리하지 않는다.

## 보정 정책 선택

검토한 대안은 raw 점수에 감지 신뢰도를 곱하는 방식과 감지 구간별 최고점 상한 방식이다. 곱셈 방식은 감지율이 조금만 낮아도 점수가 급격히 떨어지고 기존 결과와 비교하기 어렵다. 현재 구현은 raw metric과 raw 점수를 보존하면서 낮은 검출 근거로 과도한 고득점만 막을 수 있는 구간별 상한 방식을 사용한다. 신뢰도가 낮은 항목을 종합점수에서 제외하고 나머지 가중치를 재정규화하는 방식은 종합점수 의미가 작업마다 달라지므로 적용하지 않는다.

## 한계

현재 표정 분석은 감정 인식 모델이 아니라 입 모양과 FaceMesh landmark 기반 휴리스틱이다. 얼굴이 너무 작거나 화면 밖으로 벗어나면 감지율이 낮아지고 표정 점수의 최고점이 제한된다. 장기 개선 항목은 더 강한 얼굴 detector 도입, MediaPipe Tasks Face Landmarker 전환, 얼굴 크기 촬영 가이드, 업로드 품질 경고이다.
