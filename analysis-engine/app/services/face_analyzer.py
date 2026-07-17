from __future__ import annotations

from app.services.vision_metrics import analyze_vision_metrics


class FaceAnalyzer:
    # 함수 역할: 얼굴 랜드마크를 이용해 표정의 자연스러움, 긴장도, 미소 비율을 추정합니다.
    def analyze(self, duration_seconds: float, file_path: str | None = None) -> dict:
        metrics = analyze_vision_metrics(file_path, duration_seconds)
        return {
            "naturalExpressionRatio": metrics["naturalExpressionRatio"],
            "tenseExpressionRatio": metrics["tenseExpressionRatio"],
            "smileRatio": metrics["smileRatio"],
            "emotionRatio": metrics["emotionRatio"],
            "faceAttemptedFrameCount": metrics.get("faceAttemptedFrameCount"),
            "faceFullFrameDetectedCount": metrics.get("faceFullFrameDetectedCount"),
            "faceCropRetryAttemptedCount": metrics.get("faceCropRetryAttemptedCount"),
            "faceCropRetryDetectedCount": metrics.get("faceCropRetryDetectedCount"),
            "faceDetectedFrameCount": metrics.get("faceDetectedFrameCount"),
            "faceInputWidth": metrics.get("faceInputWidth"),
            "faceInputHeight": metrics.get("faceInputHeight"),
            "faceCropRetryEnabled": metrics.get("faceCropRetryEnabled"),
        }
