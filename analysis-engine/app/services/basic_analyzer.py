from __future__ import annotations

import logging

from app.services.audio_analyzer import AudioAnalyzer
from app.services.face_analyzer import FaceAnalyzer
from app.services.gaze_analyzer import GazeAnalyzer
from app.services.gesture_analyzer import GestureAnalyzer
from app.services.pose_analyzer import PoseAnalyzer
from app.services.score_calculator import ScoreCalculator
from app.services.stt_service import SttService
from app.services.video_info import VideoInfoService


logger = logging.getLogger("uvicorn.error")


class BasicAnalyzer:
    # 함수 역할: 하나의 발표 분석 작업에 필요한 서비스들을 초기화합니다.
    def __init__(self) -> None:
        self.video_info = VideoInfoService()
        self.audio = AudioAnalyzer()
        self.pose = PoseAnalyzer()
        self.gaze = GazeAnalyzer()
        self.face = FaceAnalyzer()
        self.gesture = GestureAnalyzer()
        self.score_calculator = ScoreCalculator()
        self.stt = SttService()

    # 함수 역할: 영상 정보 확인, STT 처리, 지표 추출, 점수 계산, 응답 데이터 구성 과정을 전체적으로 조정합니다.
    def analyze(self, job_id: int, video_id: int, file_path: str, transcript: str | None = None) -> dict:
        resolved_video_path = str(self.video_info.resolve_path(file_path))

        info = self.video_info.probe(resolved_video_path)
        stt_result = self.stt.from_text(transcript) if transcript else self.stt.transcribe(resolved_video_path)
        effective_transcript = (transcript or stt_result.text or "").strip()

        metrics = {}
        metrics.update(self.audio.analyze(info.durationSeconds, effective_transcript, stt_result.confidence, resolved_video_path))
        metrics.update(self.pose.analyze(info.durationSeconds, resolved_video_path))
        metrics.update(self.gaze.analyze(info.durationSeconds, resolved_video_path))
        metrics.update(self.face.analyze(info.durationSeconds, resolved_video_path))
        metrics.update(self.gesture.analyze(info.durationSeconds, resolved_video_path))

        result = self.score_calculator.calculate(metrics)
        self._merge_expression_score_metrics(metrics, result)
        self._log_audio_metrics(job_id, info.durationSeconds, metrics)
        self._log_face_detection(job_id, metrics)
        self._log_score_calculation(job_id, metrics, result)
        observations = self.score_calculator.observations(result["scores"], info.durationSeconds)
        stt_payload = stt_result.to_dict()
        summary = result["summary"]
        summary["transcript"] = effective_transcript
        summary["stt"] = stt_payload

        return {
            "jobId": job_id,
            "videoId": video_id,
            "durationSeconds": info.durationSeconds,
            "videoInfo": info.to_dict(),
            "transcript": effective_transcript,
            "stt": stt_payload,
            "metrics": metrics,
            "scores": result["scores"],
            "totalScore": result["totalScore"],
            "summary": summary,
            "observations": observations,
        }

    @staticmethod
    def _merge_expression_score_metrics(metrics: dict, result: dict) -> None:
        for item in result.get("scores", []):
            if item.get("category") != "EXPRESSION":
                continue
            expression_metrics = item.get("metrics") or {}
            for key in (
                "expressionRawScore",
                "expressionConfidence",
                "expressionConfidenceCap",
                "expressionLowConfidence",
                "expressionAnalysisReliable",
                "expressionConfidenceReason",
                "expressionFallback",
            ):
                if key in expression_metrics:
                    metrics[key] = expression_metrics[key]
            return

    @staticmethod
    def _log_audio_metrics(job_id: int, duration_seconds: float, metrics: dict) -> None:
        logger.info(
            "Audio VAD completed: "
            "jobId=%s "
            "requestedProvider=%s "
            "actualProvider=%s "
            "speechSegmentCount=%s "
            "speechSeconds=%.1f "
            "pauseCount=%s "
            "totalPauseSeconds=%.1f "
            "longestPauseSeconds=%.1f "
            "pauseRatio=%.3f "
            "fallbackUsed=%s "
            "fallbackReason=%s "
            "processingTimeMs=%.1f",
            job_id,
            metrics.get("requestedVadProvider"),
            metrics.get("actualVadProvider"),
            metrics.get("speechSegmentCount"),
            BasicAnalyzer._safe_float(metrics.get("speechSeconds"), 0.0),
            metrics.get("pauseCount"),
            BasicAnalyzer._safe_float(metrics.get("totalPauseSeconds"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("longestPauseSeconds"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("pauseRatio"), 0.0),
            metrics.get("vadFallbackUsed"),
            metrics.get("vadFallbackReason"),
            BasicAnalyzer._safe_float(metrics.get("audioVadProcessingTimeMs"), 0.0),
        )

        fallback = bool(metrics.get("audioMetricsFallback"))
        if fallback:
            logger.info(
                "Audio metrics fallback: jobId=%s reason=%s",
                job_id,
                metrics.get("audioMetricsFallbackReason"),
            )
            return
        logger.info(
            "Audio metrics: "
            "jobId=%s "
            "durationSeconds=%.1f "
            "speechSeconds=%.1f "
            "totalPauseSeconds=%.1f "
            "pauseCount=%s "
            "longestPauseSeconds=%.1f "
            "pauseRatio=%.3f "
            "meanVolumeDb=%.1f "
            "volumeStdDevDb=%.1f "
            "clippingRatio=%.3f "
            "volumeStabilityRatio=%.3f "
            "fallbackUsed=false",
            job_id,
            duration_seconds,
            BasicAnalyzer._safe_float(metrics.get("speechSeconds"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("totalPauseSeconds"), 0.0),
            metrics.get("pauseCount"),
            BasicAnalyzer._safe_float(metrics.get("longestPauseSeconds"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("pauseRatio"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("meanVolumeDb"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("volumeStdDevDb"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("clippingRatio"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("volumeStabilityRatio"), 0.0),
        )

    @staticmethod
    def _log_face_detection(job_id: int, metrics: dict) -> None:
        attempted = int(BasicAnalyzer._safe_float(metrics.get("faceAttemptedFrameCount"), 0.0))
        detected = int(BasicAnalyzer._safe_float(metrics.get("faceDetectedFrameCount"), 0.0))
        if attempted <= 0:
            return
        logger.info(
            "Face analysis started: jobId=%s sampledFrames=%s inputWidth=%s",
            job_id,
            attempted,
            metrics.get("faceInputWidth"),
        )
        logger.info(
            "Face detection summary: jobId=%s attemptedFrames=%s fullFrameDetected=%s cropRetryAttempted=%s "
            "cropRetryDetected=%s totalDetectedFrames=%s faceDetectionRatio=%.3f fallbackUsed=%s",
            job_id,
            attempted,
            metrics.get("faceFullFrameDetectedCount"),
            metrics.get("faceCropRetryAttemptedCount"),
            metrics.get("faceCropRetryDetectedCount"),
            detected,
            BasicAnalyzer._safe_float(metrics.get("faceDetectionRatio"), 0.0),
            detected == 0,
        )
        if detected == 0:
            logger.info(
                "Face analysis fallback: jobId=%s reason=face_not_detected attemptedFrames=%s detectedFrames=0",
                job_id,
                attempted,
            )

    def _log_score_calculation(self, job_id: int, metrics: dict, result: dict) -> None:
        scores = {item.get("category"): item for item in result.get("scores", [])}
        expression_metrics = scores.get("EXPRESSION", {}).get("metrics", {})

        speed = self._safe_float(metrics.get("speechSpeedSpm"), 285.0)
        pause_ratio = self._safe_float(metrics.get("pauseRatio"), 0.08)
        filler_ratio = self._safe_float(metrics.get("fillerWordRatio"), 0.015)
        filler_count = self._safe_float(metrics.get("fillerWordCount"), 0.0)

        speech_speed_score = self.score_calculator._range_score(
            speed,
            lower=220,
            ideal_low=260,
            ideal_high=330,
            upper=380,
        )
        pause_score = self.score_calculator._clamp(100.0 - min(18.0, pause_ratio * 120.0))
        filler_score = self.score_calculator._clamp(100.0 - min(15.0, filler_ratio * 400.0))

        logger.info(
            "Score calculation: "
            "jobId=%s "
            "postureRawMetric=shoulderTiltDegrees:%.3f,headMovementRatio:%.3f,postureStabilityRatio:%.3f "
            "postureScore=%.1f "
            "gazeRawMetric=eyeContactRatio:%.3f "
            "gazeScore=%.1f "
            "gestureRawMetric=movementRatio:%.3f,countPerMinute:%.3f,stillRatio:%.3f,"
            "activityScore:%.1f,averageMovement:%.5f,visibleRatio:%.3f,validFrames:%s,sampledFrames:%s,"
            "fallback:%s "
            "gestureScore=%.1f "
            "expressionRawMetric=naturalRatio:%.3f,tenseRatio:%.3f,smileRatio:%.3f,faceDetectionRatio:%.3f "
            "expressionRawScore=%.1f "
            "expressionConfidence=%.2f "
            "expressionConfidenceCap=%.1f "
            "expressionLowConfidence=%s "
            "expressionFallback=%s "
            "expressionScore=%.1f "
            "speechSpeed=%.1f "
            "speechSpeedScore=%.1f "
            "pauseRatio=%.3f "
            "pauseScore=%.1f "
            "fillerCount=%.0f "
            "fillerScore=%.1f "
            "logicScore=%s "
            "totalScore=%.1f",
            job_id,
            self._safe_float(metrics.get("shoulderTiltDegrees"), 0.0),
            self._safe_float(metrics.get("headMovementRatio"), 0.0),
            self._safe_float(metrics.get("postureStabilityRatio"), 0.0),
            self._score(scores, "POSTURE"),
            self._safe_float(metrics.get("eyeContactRatio"), 0.0),
            self._score(scores, "EYE_CONTACT"),
            self._safe_float(metrics.get("gestureMovementRatio"), 0.0),
            self._safe_float(metrics.get("gestureCountPerMinute"), 0.0),
            self._safe_float(metrics.get("stillHandRatio"), 0.0),
            self._safe_float(metrics.get("gestureActivityScore"), 0.0),
            self._safe_float(metrics.get("gestureAverageMovement"), 0.0),
            self._safe_float(metrics.get("gestureVisibleRatio"), 0.0),
            metrics.get("gestureValidFrameCount"),
            metrics.get("gestureSampledFrameCount"),
            metrics.get("gestureAnalysisFallback"),
            self._score(scores, "GESTURE"),
            self._safe_float(metrics.get("naturalExpressionRatio"), 0.0),
            self._safe_float(metrics.get("tenseExpressionRatio"), 0.0),
            self._safe_float(metrics.get("smileRatio"), 0.0),
            self._safe_float(metrics.get("faceDetectionRatio"), 0.0),
            self._safe_float(expression_metrics.get("expressionRawScore"), 0.0),
            self._safe_float(expression_metrics.get("expressionConfidence"), 0.0),
            self._safe_float(expression_metrics.get("expressionConfidenceCap"), 0.0),
            expression_metrics.get("expressionLowConfidence"),
            expression_metrics.get("expressionFallback"),
            self._score(scores, "EXPRESSION"),
            speed,
            speech_speed_score,
            pause_ratio,
            pause_score,
            filler_count,
            filler_score,
            "NA",
            self._safe_float(result.get("totalScore"), 0.0),
        )

    @staticmethod
    def _score(scores: dict, category: str) -> float:
        return BasicAnalyzer._safe_float(scores.get(category, {}).get("score"), 0.0)

    @staticmethod
    def _safe_float(value, default: float) -> float:
        try:
            if value is None:
                return default
            return float(value)
        except (TypeError, ValueError):
            return default
