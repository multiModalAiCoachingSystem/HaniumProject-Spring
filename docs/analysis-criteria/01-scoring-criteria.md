# 전체 평가 점수

## 목적

발표 영상을 음성, 시각, 제스처 지표로 나누어 0~100점 사이의 최종 점수와 항목별 피드백을 만든다. 현재 구현 기준은 `analysis-engine/app/services/score_calculator.py`의 `ScoreCalculator`와 `WEIGHTS`이다.

## 입력 데이터

- `metrics`: `BasicAnalyzer`가 오디오, 자세, 시선, 표정, 임시 제스처 지표를 병합한 딕셔너리
- 주요 입력 키: `shoulderTiltDegrees`, `headMovementRatio`, `postureStabilityRatio`, `naturalExpressionRatio`, `tenseExpressionRatio`, `smileRatio`, `eyeContactRatio`, `speechSpeedSpm`, `volumeStabilityRatio`, `pauseRatio`, `fillerWordRatio`, `gestureMovementRatio`, `gestureCountPerMinute`, `stillHandRatio`

## 계산식

최종 점수는 항목별 점수에 가중치를 곱해 합산한다.

```text
totalScore = round(sum(score * weight), 1)
```

가중치는 다음과 같다.

| category | name | weight |
|---|---:|---:|
| `POSTURE` | 자세 | 0.25 |
| `EXPRESSION` | 표정 | 0.20 |
| `EYE_CONTACT` | 시선 | 0.20 |
| `VOICE` | 음성 | 0.25 |
| `GESTURE` | 제스처 | 0.10 |

## 기준값

기준값은 `score_calculator.py`의 각 점수 함수에 하드코딩되어 있다. 변경 가능성이 있으므로 기준값을 수정할 때는 해당 파일의 `posture_score`, `expression_score`, `eye_contact_score`, `voice_score`, `gesture_score`를 함께 확인해야 한다.

## 점수화

- 항목별 점수는 `_clamp`로 0~100 범위에 제한된다.
- 등급은 `_grade`에서 계산한다.
- `score >= 90`: `우수`
- `score >= 80`: `양호`
- `score >= 70`: `보통`
- 그 외: `개선 필요`

## 출력

`/api/basic-analysis` 응답의 `scores`, `totalScore`, `summary`, `observations`에 반영된다. 백엔드는 이 값을 `final_analysis_results.total_score`, `scores_json`, `summary_json`, `feedback_json`으로 저장한다.

## 예외 처리

계산 함수는 입력 지표가 없으면 기본값을 사용한다. 예를 들어 자세 지표가 없으면 `shoulderTiltDegrees=6.0`, `headMovementRatio=0.12`, `postureStabilityRatio=0.78`이 사용된다.

## 한계

현재 최종 점수는 `analysis-engine`의 정량 계산 결과만 사용한다. `video-llm-engine`의 Qwen-VL 관찰 결과는 피드백 목록에 병합되지만 `totalScore`에는 반영되지 않는다.
