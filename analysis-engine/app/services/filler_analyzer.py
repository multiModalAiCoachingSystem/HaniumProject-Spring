# 역할: analysis-engine의 filler_analyzer 기능을 담당하는 Python 코드입니다.
from __future__ import annotations


class FillerAnalyzer:
    FILLERS = ["음", "어", "그", "약간", "뭔가", "이제", "저"]

    # 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    def analyze(self, transcript: str | None) -> dict:
        transcript = transcript or ""
        count = sum(transcript.count(word) for word in self.FILLERS)
        words = max(1, len(transcript.split()))
        return {
            "fillerWordCount": count,
            "fillerWordRatio": round(count / words, 4),
        }
