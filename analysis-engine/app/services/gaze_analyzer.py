from __future__ import annotations

from app.services.vision_metrics import analyze_vision_metrics


class GazeAnalyzer:
    # 함수 역할: 얼굴 랜드마크를 이용해 발표자가 카메라 또는 청중을 바라보는 시선 비율을 추정합니다.
    def analyze(self, duration_seconds: float, file_path: str | None = None) -> dict:
        metrics = analyze_vision_metrics(file_path, duration_seconds)
        return {
            "eyeContactRatio": metrics["eyeContactRatio"],
            "gazeAwayRatio": metrics["gazeAwayRatio"],
            "faceDetectionRatio": metrics["faceDetectionRatio"],
        }
