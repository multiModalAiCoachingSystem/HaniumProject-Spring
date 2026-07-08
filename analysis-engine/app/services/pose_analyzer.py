from __future__ import annotations

from app.services.vision_metrics import analyze_vision_metrics


class PoseAnalyzer:
    # 함수 역할: 영상에서 일정 간격으로 추출한 프레임을 분석하여 자세 관련 지표를 계산합니다.
    def analyze(self, duration_seconds: float, file_path: str | None = None) -> dict:
        metrics = analyze_vision_metrics(file_path, duration_seconds)
        return {
            "shoulderTiltDegrees": metrics["shoulderTiltDegrees"],
            "headMovementRatio": metrics["headMovementRatio"],
            "postureStabilityRatio": metrics["postureStabilityRatio"],
            "visionFrameCount": metrics["visionFrameCount"],
            "poseDetectionRatio": metrics["poseDetectionRatio"],
            "visionAnalysisFallback": metrics["visionAnalysisFallback"],
            "visionAnalysisFallbackReason": metrics.get("visionAnalysisFallbackReason"),
        }
