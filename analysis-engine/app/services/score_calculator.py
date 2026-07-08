# 역할: 분석 지표를 항목별 점수와 최종 점수, 기본 코칭 피드백으로 변환합니다.
from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Any


WEIGHTS = {
    "POSTURE": 0.25,
    "EXPRESSION": 0.20,
    "EYE_CONTACT": 0.20,
    "VOICE": 0.25,
    "GESTURE": 0.10,
}


@dataclass(frozen=True)
class ScoreItem:
    category: str
    name: str
    score: float
    weight: float
    grade: str
    summary: str
    metrics: dict[str, Any]

    # 함수 역할: 객체 데이터를 API 응답에 적합한 딕셔너리 형태로 변환합니다.
    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class ScoreCalculator:
    # 함수 역할: calculate에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def calculate(self, metrics: dict[str, Any]) -> dict[str, Any]:
        posture = self.posture_score(metrics)
        expression = self.expression_score(metrics)
        eye_contact = self.eye_contact_score(metrics)
        voice = self.voice_score(metrics)
        gesture = self.gesture_score(metrics)
        scores = [posture, expression, eye_contact, voice, gesture]
        total = round(sum(item.score * item.weight for item in scores), 1)

        return {
            "totalScore": total,
            "scores": [item.to_dict() for item in scores],
            "summary": self.summary(total, scores),
        }

    # 함수 역할: posture_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    def posture_score(self, metrics: dict[str, Any]) -> ScoreItem:
        shoulder_tilt = float(metrics.get("shoulderTiltDegrees", 6.0))
        head_movement = float(metrics.get("headMovementRatio", 0.12))
        stability = float(metrics.get("postureStabilityRatio", 0.78))

        tilt_score = self._piecewise_lower_is_better(
            shoulder_tilt,
            [(5, 100), (10, 85), (20, 70), (999, 50)],
        )
        movement_score = self._linear_score(head_movement, best=0.05, worst=0.35, reverse=True)
        stability_score = self._ratio_score(stability)
        score = self._avg([tilt_score, movement_score, stability_score])

        return self._item(
            "POSTURE",
            "자세",
            score,
            WEIGHTS["POSTURE"],
            "어깨 수평, 고개 움직임, 상체 안정성을 기준으로 계산했습니다.",
            {
                "shoulderTiltDegrees": shoulder_tilt,
                "headMovementRatio": head_movement,
                "postureStabilityRatio": stability,
            },
        )

    # 함수 역할: expression_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    def expression_score(self, metrics: dict[str, Any]) -> ScoreItem:
        natural_ratio = float(metrics.get("naturalExpressionRatio", 0.74))
        tense_ratio = float(metrics.get("tenseExpressionRatio", 0.12))
        smile_ratio = float(metrics.get("smileRatio", 0.18))

        natural_score = self._ratio_score(natural_ratio)
        tension_penalty = min(35.0, tense_ratio * 100.0)
        smile_bonus = min(8.0, smile_ratio * 20.0)
        score = self._clamp(natural_score - tension_penalty + smile_bonus)

        return self._item(
            "EXPRESSION",
            "표정",
            score,
            WEIGHTS["EXPRESSION"],
            "자연스러운 표정 유지 시간과 긴장 표정 비율을 반영했습니다.",
            {
                "naturalExpressionRatio": natural_ratio,
                "tenseExpressionRatio": tense_ratio,
                "smileRatio": smile_ratio,
            },
        )

    # 함수 역할: eye_contact_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    def eye_contact_score(self, metrics: dict[str, Any]) -> ScoreItem:
        ratio = float(metrics.get("eyeContactRatio", 0.68))
        if ratio >= 0.70:
            score = 92 + min(8, (ratio - 0.70) * 25)
        elif ratio >= 0.40:
            score = 70 + (ratio - 0.40) / 0.30 * 18
        else:
            score = 45 + ratio / 0.40 * 20

        return self._item(
            "EYE_CONTACT",
            "시선",
            self._clamp(score),
            WEIGHTS["EYE_CONTACT"],
            "카메라 또는 청중 방향 응시 비율을 기준으로 계산했습니다.",
            {"eyeContactRatio": ratio},
        )

    # 함수 역할: voice_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    def voice_score(self, metrics: dict[str, Any]) -> ScoreItem:
        speed = float(metrics.get("speechSpeedSpm", 285.0))
        volume_stability = float(metrics.get("volumeStabilityRatio", 0.78))
        pause_ratio = float(metrics.get("pauseRatio", 0.08))
        filler_ratio = float(metrics.get("fillerWordRatio", 0.015))

        speed_score = self._range_score(speed, lower=220, ideal_low=260, ideal_high=330, upper=380)
        volume_score = self._ratio_score(volume_stability)
        pause_penalty = min(18.0, pause_ratio * 120.0)
        filler_penalty = min(15.0, filler_ratio * 400.0)
        score = self._clamp(self._avg([speed_score, volume_score]) - pause_penalty - filler_penalty)

        return self._item(
            "VOICE",
            "음성",
            score,
            WEIGHTS["VOICE"],
            "발화 속도, 침묵 비율, 불필요한 표현, 음량 안정성을 함께 반영했습니다.",
            {
                "speechSpeedSpm": speed,
                "volumeStabilityRatio": volume_stability,
                "pauseRatio": pause_ratio,
                "fillerWordRatio": filler_ratio,
            },
        )

    # 함수 역할: gesture_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    def gesture_score(self, metrics: dict[str, Any]) -> ScoreItem:
        movement_ratio = float(metrics.get("gestureMovementRatio", 0.46))
        count_per_min = float(metrics.get("gestureCountPerMinute", 4.2))
        still_ratio = float(metrics.get("stillHandRatio", 0.18))
        activity_score = metrics.get("gestureActivityScore")

        movement_score = self._range_score(movement_ratio, lower=0.15, ideal_low=0.30, ideal_high=0.65, upper=0.85)
        count_score = self._range_score(count_per_min, lower=1.0, ideal_low=3.0, ideal_high=8.0, upper=14.0)
        still_penalty = min(18.0, still_ratio * 45.0)
        score = self._clamp(self._avg([movement_score, count_score]) - still_penalty)
        if activity_score is not None:
            score = self._clamp(score * 0.55 + float(activity_score) * 0.45)

        return self._item(
            "GESTURE",
            "제스처",
            score,
            WEIGHTS["GESTURE"],
            "손과 팔의 움직임 빈도와 과다/부족 패턴을 기준으로 계산했습니다.",
            {
                "gestureMovementRatio": movement_ratio,
                "gestureCountPerMinute": count_per_min,
                "stillHandRatio": still_ratio,
                "gestureActivityScore": activity_score,
                "gestureAverageMovement": metrics.get("gestureAverageMovement"),
                "gestureBalance": metrics.get("gestureBalance"),
                "gestureStability": metrics.get("gestureStability"),
                "gestureVisibleRatio": metrics.get("gestureVisibleRatio"),
                "gestureAnalysisFallback": metrics.get("gestureAnalysisFallback"),
            },
        )

    # 함수 역할: summary에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    def summary(self, total: float, scores: list[ScoreItem]) -> dict[str, Any]:
        strongest = max(scores, key=lambda item: item.score)
        weakest = min(scores, key=lambda item: item.score)
        return {
            "overall": self._overall_text(total),
            "strengths": [f"{strongest.name} 영역이 가장 안정적입니다."],
            "weaknesses": [f"{weakest.name} 영역을 우선 개선하면 전체 점수가 좋아집니다."],
            "priority": weakest.category,
            "practice": self._practice_text(weakest.category),
        }

    # 함수 역할: observations에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def observations(self, scores: list[dict[str, Any]], duration_seconds: float | None = None) -> list[dict[str, Any]]:
        observations = []
        for index, item in enumerate(scores, start=1):
            score = float(item["score"])
            category = item["category"]
            name = item["name"]
            deductions = self._deduction_reasons(category, item.get("metrics", {}))
            problem_text = " ".join(deductions) if deductions else "전체적으로 안정적인 모습입니다."

            if score >= 85:
                kind = "STRENGTH"
                title = f"{name} 강점"
                suggestion = self._keep_strength_text(category)
            else:
                kind = "WEAKNESS"
                title = f"{name} 개선 필요"
                suggestion = self._practice_text(category)

            start_seconds, end_seconds = self._representative_window(index, len(scores), duration_seconds)
            observations.append(
                {
                    "id": index,
                    "type": kind,
                    "category": category,
                    "startSeconds": start_seconds,
                    "endSeconds": end_seconds,
                    "title": title,
                    "description": problem_text,
                    "suggestion": suggestion,
                }
            )
        return observations

    # 함수 역할: _representative_window에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _representative_window(index: int, total: int, duration_seconds: float | None) -> tuple[float, float]:
        duration = max(float(duration_seconds or 0.0), 0.0)
        if duration <= 0:
            return 0.0, 0.0

        total = max(total, 1)
        segment = duration / total
        start = max(0.0, segment * (index - 1))
        end = min(duration, start + segment)

        if end - start > 30.0:
            center = start + (end - start) / 2.0
            start = max(0.0, center - 15.0)
            end = min(duration, center + 15.0)
        elif end - start < 8.0 and duration >= 8.0:
            end = min(duration, start + 8.0)

        return round(start, 3), round(end, 3)

    # 함수 역할: _deduction_reasons에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _deduction_reasons(self, category: str, metrics: dict[str, Any]) -> list[str]:
        return {
            "POSTURE": self._posture_deductions,
            "EXPRESSION": self._expression_deductions,
            "EYE_CONTACT": self._eye_contact_deductions,
            "VOICE": self._voice_deductions,
            "GESTURE": self._gesture_deductions,
        }.get(category, lambda _: [])(metrics)

    # 함수 역할: _posture_deductions에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _posture_deductions(metrics: dict[str, Any]) -> list[str]:
        reasons = []
        shoulder_tilt = float(metrics.get("shoulderTiltDegrees", 0.0))
        head_movement = float(metrics.get("headMovementRatio", 0.0))
        stability = float(metrics.get("postureStabilityRatio", 1.0))
        if shoulder_tilt > 5:
            reasons.append("어깨가 한쪽으로 기울어져 자세가 불안정해 보입니다.")
        if head_movement > 0.05:
            reasons.append("고개 움직임이 많아 시선과 자세가 흔들려 보입니다.")
        if stability < 0.9:
            reasons.append("상체가 흔들려 발표 자세가 안정적으로 보이지 않습니다.")
        return reasons

    # 함수 역할: _expression_deductions에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _expression_deductions(metrics: dict[str, Any]) -> list[str]:
        reasons = []
        natural_ratio = float(metrics.get("naturalExpressionRatio", 1.0))
        tense_ratio = float(metrics.get("tenseExpressionRatio", 0.0))
        smile_ratio = float(metrics.get("smileRatio", 0.0))
        if natural_ratio < 0.9:
            reasons.append("표정 변화가 적어 발표가 다소 딱딱하게 보입니다.")
        if tense_ratio > 0.05:
            reasons.append("긴장된 표정이 보여 전달 분위기가 딱딱해질 수 있습니다.")
        if smile_ratio < 0.15:
            reasons.append("밝은 표정이 부족해 청중에게 친근하게 전달되기 어렵습니다.")
        return reasons

    # 함수 역할: _eye_contact_deductions에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _eye_contact_deductions(metrics: dict[str, Any]) -> list[str]:
        ratio = float(metrics.get("eyeContactRatio", 1.0))
        if ratio < 0.7:
            return ["청중 또는 카메라를 바라보는 시간이 부족합니다."]
        return []

    # 함수 역할: _voice_deductions에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _voice_deductions(metrics: dict[str, Any]) -> list[str]:
        reasons = []
        speed = float(metrics.get("speechSpeedSpm", 0.0))
        volume_stability = float(metrics.get("volumeStabilityRatio", 1.0))
        pause_ratio = float(metrics.get("pauseRatio", 0.0))
        filler_ratio = float(metrics.get("fillerWordRatio", 0.0))
        if speed < 260:
            reasons.append("말하는 속도가 느려 발표 흐름이 늘어질 수 있습니다.")
        elif speed > 330:
            reasons.append("말하는 속도가 빨라 핵심 내용이 충분히 전달되기 어렵습니다.")
        if volume_stability < 0.9:
            reasons.append("목소리 크기가 일정하지 않아 중요한 부분이 덜 또렷하게 들릴 수 있습니다.")
        if pause_ratio > 0.05:
            reasons.append("중간 침묵이 많아 발표 흐름이 끊겨 보입니다.")
        if filler_ratio > 0.01:
            reasons.append("불필요한 표현이 반복되어 말의 밀도가 낮아질 수 있습니다.")
        return reasons

    # 함수 역할: _gesture_deductions에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _gesture_deductions(metrics: dict[str, Any]) -> list[str]:
        reasons = []
        movement_ratio = float(metrics.get("gestureMovementRatio", 0.0))
        count_per_min = float(metrics.get("gestureCountPerMinute", 0.0))
        still_ratio = float(metrics.get("stillHandRatio", 0.0))
        if movement_ratio < 0.3:
            reasons.append("손동작이 부족해 강조해야 할 부분이 잘 살아나지 않습니다.")
        elif movement_ratio > 0.65:
            reasons.append("손동작이 많아 발표가 산만해 보일 수 있습니다.")
        if count_per_min < 3:
            reasons.append("핵심 문장을 강조하는 제스처가 부족합니다.")
        elif count_per_min > 8:
            reasons.append("제스처 빈도가 많아 시선이 손동작으로 분산될 수 있습니다.")
        if still_ratio > 0.1:
            reasons.append("손이 멈춰 있는 시간이 길어 발표가 경직되어 보일 수 있습니다.")
        return reasons

    # 함수 역할: _item에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _item(self, category: str, name: str, score: float, weight: float, summary: str, metrics: dict[str, Any]) -> ScoreItem:
        rounded = round(self._clamp(score), 1)
        return ScoreItem(category, name, rounded, weight, self._grade(rounded), summary, metrics)

    # 함수 역할: _grade에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    @staticmethod
    def _grade(score: float) -> str:
        if score >= 90:
            return "우수"
        if score >= 80:
            return "양호"
        if score >= 70:
            return "보통"
        return "개선 필요"

    # 함수 역할: _overall_text에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _overall_text(total: float) -> str:
        if total >= 85:
            return "전반적으로 안정적인 발표입니다. 강점은 유지하고 낮은 항목만 보완하면 됩니다."
        if total >= 70:
            return "기본 흐름은 괜찮지만 일부 전달 요소를 개선하면 발표 완성도가 올라갑니다."
        return "자세, 시선, 음성, 제스처 중 낮은 항목부터 단계적으로 연습하는 것이 좋습니다."

    # 함수 역할: _keep_strength_text에서 사용자가 볼 강점과 개선점 문구를 구성합니다.
    @staticmethod
    def _keep_strength_text(category: str) -> str:
        return {
            "POSTURE": "현재 자세 흐름은 유지하고, 어깨와 상체가 흔들리지 않게 한 번 더 고정해보세요.",
            "EXPRESSION": "현재 표정 흐름은 유지하고, 핵심 문장 앞뒤에 자연스러운 미소를 조금 더 넣어보세요.",
            "EYE_CONTACT": "현재 시선 흐름은 유지하고, 문장 끝마다 카메라나 청중을 한 번 더 바라보세요.",
            "VOICE": "현재 발화 흐름은 유지하고, 핵심 단어 앞뒤에서 속도와 음량을 조금 더 조절해보세요.",
            "GESTURE": "현재 제스처 흐름은 유지하고, 핵심 문장에서만 손동작을 또렷하게 사용해보세요.",
        }.get(category, "현재 좋은 흐름은 유지하고, 아쉬운 부분만 한 번 더 다듬어보세요.")

    # 함수 역할: _practice_text에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _practice_text(category: str) -> str:
        return {
            "POSTURE": "어깨를 수평으로 맞춘 뒤 1분 발표를 녹화해 상체 흔들림을 확인해보세요.",
            "EXPRESSION": "핵심 문장을 말하기 전후로 표정을 풀고, 카메라를 보며 자연스럽게 말하는 연습을 해보세요.",
            "EYE_CONTACT": "문장 단위로 고개를 들고 카메라나 청중을 바라보는 연습을 반복해보세요.",
            "VOICE": "문장 사이에 짧게 멈추고, 너무 빠른 구간은 속도를 낮춰 또박또박 말해보세요.",
            "GESTURE": "강조 문장마다 손동작 하나만 정해서 사용하고, 불필요한 움직임은 줄여보세요.",
        }.get(category, "가장 아쉬운 부분을 기준으로 짧은 발표를 다시 녹화해 비교해보세요.")

    # 함수 역할: _clamp에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _clamp(value: float, low: float = 0.0, high: float = 100.0) -> float:
        return max(low, min(high, value))

    # 함수 역할: _avg에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @classmethod
    def _avg(cls, values: list[float]) -> float:
        return cls._clamp(sum(values) / len(values))

    # 함수 역할: _ratio_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    @classmethod
    def _ratio_score(cls, ratio: float) -> float:
        return cls._clamp(ratio * 100.0)

    # 함수 역할: _linear_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    @classmethod
    def _linear_score(cls, value: float, best: float, worst: float, reverse: bool = False) -> float:
        if reverse:
            if value <= best:
                return 100.0
            if value >= worst:
                return 50.0
            return 100.0 - (value - best) / (worst - best) * 50.0
        if value >= best:
            return 100.0
        if value <= worst:
            return 50.0
        return 50.0 + (value - worst) / (best - worst) * 50.0

    # 함수 역할: _piecewise_lower_is_better에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _piecewise_lower_is_better(value: float, bands: list[tuple[float, float]]) -> float:
        for limit, score in bands:
            if value <= limit:
                return float(score)
        return float(bands[-1][1])

    # 함수 역할: _range_score에서 분석 지표를 점수, 등급, 요약 피드백으로 변환합니다.
    @classmethod
    def _range_score(cls, value: float, lower: float, ideal_low: float, ideal_high: float, upper: float) -> float:
        if ideal_low <= value <= ideal_high:
            return 100.0
        if lower <= value < ideal_low:
            return 65.0 + (value - lower) / (ideal_low - lower) * 35.0
        if ideal_high < value <= upper:
            return 100.0 - (value - ideal_high) / (upper - ideal_high) * 35.0
        return 55.0
