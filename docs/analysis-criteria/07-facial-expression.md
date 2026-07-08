# 표정

## 목적

자연스러운 표정, 긴장 표정, 미소 비율을 추정해 발표 분위기를 평가한다. 구현 위치는 `analysis-engine/app/services/vision_metrics.py`, `face_analyzer.py`, `score_calculator.py`이다.

## 입력 데이터

- `naturalExpressionRatio`: 긴장 표정이 아닌 face frame 비율
- `tenseExpressionRatio`: 입 폭이 좁거나 입 벌림이 큰 프레임 비율
- `smileRatio`: 입 폭과 입 벌림 기준으로 미소로 판단된 프레임 비율
- `emotionRatio`: `happy`, `neutral`, `anxious` 비율

## 계산식

입 모양은 FaceMesh의 얼굴 폭, 입 좌우, 윗입술/아랫입술 좌표로 계산한다.

```text
is_smile = mouth_width >= 0.36 and mouth_open <= 0.12
is_tense = mouth_width <= 0.25 or mouth_open >= 0.18
natural = not is_tense
```

표정 점수:

```text
natural_score = naturalExpressionRatio * 100
tension_penalty = min(35.0, tenseExpressionRatio * 100)
smile_bonus = min(8.0, smileRatio * 20)
expressionScore = clamp(natural_score - tension_penalty + smile_bonus)
```

## 기준값

- `naturalExpressionRatio < 0.9`: 표정 변화 부족 피드백
- `tenseExpressionRatio > 0.05`: 긴장 표정 피드백
- `smileRatio < 0.15`: 밝은 표정 부족 피드백

## 점수화

표정 점수는 `EXPRESSION` category로 반환되며 최종 점수 가중치는 0.20이다.

## 예외 처리

얼굴 프레임 분석이 실패하면 fallback으로 `naturalExpressionRatio=0.74`, `tenseExpressionRatio=0.12`, `smileRatio=0.18`을 사용한다.

## 한계

현재 표정 분석은 감정 모델이 아니라 입 모양 기반 휴리스틱이다. 발표 주제에 따라 미소가 항상 적합하지 않을 수 있는데, 이 맥락 판단은 미구현이다.
