# 전체 평가 점수

## 목적

발표 영상을 음성, 자세, 시선, 표정, 제스처 지표로 나누어 0~100점 범위의 항목별 점수와 최종 점수를 만든다. 현재 구현 기준 파일은 `analysis-engine/app/services/score_calculator.py`이다.

## 입력 데이터

`BasicAnalyzer`가 audio, pose, gaze, face, gesture 분석 결과를 하나의 `metrics` 딕셔너리로 합친 뒤 `ScoreCalculator.calculate(metrics)`에 전달한다.

주요 입력 키:

- 자세: `shoulderTiltDegrees`, `headMovementRatio`, `postureStabilityRatio`
- 표정: `naturalExpressionRatio`, `tenseExpressionRatio`, `smileRatio`, `faceDetectionRatio`, `visionAnalysisFallback`
- 시선: `eyeContactRatio`
- 음성: `speechSpeedSpm`, `volumeStabilityRatio`, `pauseRatio`, `fillerWordRatio`
- 제스처: `gestureMovementRatio`, `gestureCountPerMinute`, `stillHandRatio`, `gestureActivityScore`, `gestureVisibleRatio`

## 최종 점수 계산

```text
totalScore = round(sum(categoryScore * weight), 1)
```

현재 가중치:

| category | 항목 | weight |
|---|---:|---:|
| `POSTURE` | 자세 | 0.25 |
| `EXPRESSION` | 표정 | 0.20 |
| `EYE_CONTACT` | 시선 | 0.20 |
| `VOICE` | 음성 | 0.25 |
| `GESTURE` | 제스처 | 0.10 |

## 점수화 공통 원칙

- 항목별 점수는 `_clamp()`로 0~100 범위에 제한된다.
- 최종 점수 가중치는 변경하지 않는다.
- Video LLM과 Text LLM 결과는 정성 피드백에 사용되며 `totalScore`를 직접 덮어쓰지 않는다.
- 표정 점수는 얼굴 감지율이 낮으면 최고점이 제한된다.

## 등급

| 점수 범위 | 등급 |
|---:|---|
| 90 이상 | 우수 |
| 80 이상 | 양호 |
| 70 이상 | 보통 |
| 70 미만 | 개선 필요 |

## fallback

분석 지표가 없거나 vision/audio 분석이 실패하면 각 분석기의 fallback metric이 사용된다. fallback은 전체 분석을 실패시키지 않기 위한 안전장치이며, 실제 분석보다 유리한 최고점으로 처리하지 않는다.

특히 표정·시선 fallback은 `visionAnalysisFallback=true`, `faceDetectionRatio=0.0`으로 전달되며 `EXPRESSION`, `EYE_CONTACT` 최고점이 50점으로 제한된다. 얼굴 감지율이 50% 미만이면 검출 프레임 수와 감지율에 따라 점수 상한을 적용하며, 종합점수 가중치는 변경하지 않는다.
