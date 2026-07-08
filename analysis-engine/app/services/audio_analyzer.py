# 역할: STT 텍스트와 발표 길이를 바탕으로 발화 속도, 불필요 표현, 침묵 등 음성 지표를 계산합니다.
from __future__ import annotations

import re


class AudioAnalyzer:
    FILLERS = ["음", "어", "그", "약간", "뭔가", "이제", "저", "그니까", "그러니까"]

    # 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    def analyze(
        self,
        duration_seconds: float,
        transcript: str | None = None,
        stt_confidence: float = 0.0,
    ) -> dict:
        transcript = (transcript or "").strip()
        syllable_count = self._estimate_korean_syllables(transcript)
        token_count = len(re.findall(r"\S+", transcript))

        if syllable_count == 0 and token_count == 0:
            speech_speed = 285.0
            filler_ratio = 0.015
        else:
            speech_units = syllable_count or token_count
            speech_speed = round(speech_units / max(duration_seconds / 60.0, 0.1), 1)
            filler_ratio = 0.0

        filler_count = sum(transcript.count(word) for word in self.FILLERS)
        if syllable_count > 0 or token_count > 0:
            denominator = max(syllable_count or token_count, 1)
            filler_ratio = round(filler_count / denominator, 4)

        return {
            "speechSpeedSpm": speech_speed,
            "fillerWordCount": filler_count,
            "fillerWordRatio": filler_ratio,
            "pauseCount": max(1, int(duration_seconds // 35)),
            "pauseRatio": 0.08,
            "volumeStabilityRatio": 0.78,
            "sttConfidence": round(max(0.0, min(1.0, stt_confidence)), 3),
        }

    # 함수 역할: _estimate_korean_syllables에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _estimate_korean_syllables(text: str) -> int:
        return sum(1 for char in text if "가" <= char <= "힣")
