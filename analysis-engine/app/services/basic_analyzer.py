from __future__ import annotations

import logging
import time

from app.services.audio_analyzer import AudioAnalyzer
from app.services.face_analyzer import FaceAnalyzer
from app.services.gaze_analyzer import GazeAnalyzer
from app.services.gesture_analyzer import GestureAnalyzer
from app.services.pose_analyzer import PoseAnalyzer
from app.services.score_calculator import ScoreCalculator
from app.services.stt_service import SttService
from app.services.video_info import VideoInfoService


logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)


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
        started = time.perf_counter()
        logger.info("[ANALYSIS] 분석을 시작합니다. jobId=%s", job_id)
        try:
            result = self._analyze(job_id, video_id, file_path, transcript)
        except Exception as exception:
            logger.error(
                "[ANALYSIS] 분석 중 오류가 발생했습니다. jobId=%s reason=%s",
                job_id,
                exception.__class__.__name__,
            )
            raise
        logger.info(
            "[ANALYSIS] 전체 분석이 끝났습니다. jobId=%s elapsed=%.2fs",
            job_id,
            time.perf_counter() - started,
        )
        return result

    def _analyze(self, job_id: int, video_id: int, file_path: str, transcript: str | None = None) -> dict:
        total_started = time.perf_counter()
        resolved_video_path = str(self.video_info.resolve_path(file_path))

        logger.info("[VIDEO] 영상 정보를 확인합니다. jobId=%s", job_id)
        probe_started = time.perf_counter()
        info = self.video_info.probe(resolved_video_path)
        probe_seconds = time.perf_counter() - probe_started
        logger.info(
            "[VIDEO] 영상 정보 확인이 끝났습니다. jobId=%s duration=%.1fs resolution=%sx%s fps=%s elapsed=%.2fs",
            job_id,
            info.durationSeconds,
            info.width if info.width is not None else "unknown",
            info.height if info.height is not None else "unknown",
            info.fps if info.fps is not None else "unknown",
            probe_seconds,
        )

        logger.info("[AUDIO] 음성 분석을 시작합니다. jobId=%s", job_id)
        audio_pipeline_started = time.perf_counter()
        text_started = time.perf_counter()
        logger.debug("[AUDIO] STT 분석을 시작합니다. jobId=%s", job_id)
        stt_result = self.stt.from_text(transcript) if transcript else self.stt.transcribe(resolved_video_path)
        effective_transcript = (transcript or stt_result.text or "").strip()
        text_seconds = time.perf_counter() - text_started

        metrics = {}
        audio_started = time.perf_counter()
        logger.debug("[AUDIO] 말하기 속도와 침묵을 측정합니다. jobId=%s", job_id)
        metrics.update(self.audio.analyze(info.durationSeconds, effective_transcript, stt_result.confidence, resolved_video_path))
        audio_seconds = time.perf_counter() - audio_started
        logger.info(
            "[AUDIO] 측정 완료. jobId=%s speechSpeed=%.1fSPM fillerCount=%s pauseCount=%s",
            job_id,
            self._safe_float(metrics.get("speechSpeedSpm"), 0.0),
            metrics.get("fillerWordCount", 0),
            metrics.get("pauseCount", 0),
        )
        logger.info(
            "[AUDIO] 음성 분석이 끝났습니다. jobId=%s elapsed=%.2fs",
            job_id,
            time.perf_counter() - audio_pipeline_started,
        )

        vision_started = time.perf_counter()
        posture_started = time.perf_counter()
        logger.info("[POSTURE] 자세 분석을 시작합니다. jobId=%s", job_id)
        metrics.update(self.pose.analyze(info.durationSeconds, resolved_video_path))
        logger.info(
            "[POSTURE] 자세 분석이 끝났습니다. jobId=%s elapsed=%.2fs shoulderTilt=%.1fdeg stability=%.1f%%",
            job_id,
            time.perf_counter() - posture_started,
            self._safe_float(metrics.get("shoulderTiltDegrees"), 0.0),
            self._safe_float(metrics.get("postureStabilityRatio"), 0.0) * 100.0,
        )

        gaze_started = time.perf_counter()
        logger.info("[GAZE] 시선 분석을 시작합니다. jobId=%s", job_id)
        metrics.update(self.gaze.analyze(info.durationSeconds, resolved_video_path))
        gaze_detection_ratio = self._safe_float(metrics.get("gazeDetectionRatio"), 0.0)
        logger.info(
            "[GAZE] 시선 분석이 끝났습니다. jobId=%s elapsed=%.2fs detectionRatio=%.1f%% eyeContactRatio=%.1f%%",
            job_id,
            time.perf_counter() - gaze_started,
            gaze_detection_ratio * 100.0,
            self._safe_float(metrics.get("eyeContactRatio"), 0.0) * 100.0,
        )
        if gaze_detection_ratio < 0.10:
            logger.warning(
                "[GAZE] 시선 분석 신뢰도가 낮습니다. jobId=%s detectionRatio=%.1f%%",
                job_id,
                gaze_detection_ratio * 100.0,
            )

        expression_started = time.perf_counter()
        logger.info("[EXPRESSION] 표정 분석을 시작합니다. jobId=%s", job_id)
        metrics.update(self.face.analyze(info.durationSeconds, resolved_video_path))
        face_detection_ratio = self._safe_float(metrics.get("faceDetectionRatio"), 0.0)
        logger.info(
            "[EXPRESSION] 표정 분석이 끝났습니다. jobId=%s elapsed=%.2fs faceDetection=%.1f%% natural=%.1f%% smile=%.1f%%",
            job_id,
            time.perf_counter() - expression_started,
            face_detection_ratio * 100.0,
            self._safe_float(metrics.get("naturalExpressionRatio"), 0.0) * 100.0,
            self._safe_float(metrics.get("smileRatio"), 0.0) * 100.0,
        )
        if face_detection_ratio < 0.10:
            logger.warning(
                "[EXPRESSION] 얼굴 감지 신뢰도가 낮습니다. jobId=%s detectionRatio=%.1f%%",
                job_id,
                face_detection_ratio * 100.0,
            )
        vision_seconds = time.perf_counter() - vision_started

        gesture_started = time.perf_counter()
        logger.info("[GESTURE] 제스처 분석을 시작합니다. jobId=%s", job_id)
        metrics.update(self.gesture.analyze(info.durationSeconds, resolved_video_path))
        gesture_seconds = time.perf_counter() - gesture_started
        logger.info(
            "[GESTURE] 제스처 분석이 끝났습니다. jobId=%s elapsed=%.2fs movementRatio=%.1f%% gesturesPerMinute=%.1f",
            job_id,
            gesture_seconds,
            self._safe_float(metrics.get("gestureMovementRatio"), 0.0) * 100.0,
            self._safe_float(metrics.get("gestureCountPerMinute"), 0.0),
        )
        if bool(metrics.get("gestureAnalysisFallback")):
            logger.warning(
                "[GESTURE] 제스처 분석 fallback을 사용합니다. jobId=%s reason=%s",
                job_id,
                metrics.get("gestureAnalysisFallbackReason") or "GESTURE_ANALYSIS_UNAVAILABLE",
            )

        score_started = time.perf_counter()
        logger.info("[SCORE] 점수 측정을 시작합니다. jobId=%s", job_id)
        result = self.score_calculator.calculate(metrics)
        score_seconds = time.perf_counter() - score_started
        score_items = {item.get("category"): item.get("score") for item in result.get("scores", [])}
        logger.info(
            "[SCORE] 항목 점수 계산 완료. jobId=%s posture=%s expression=%s gaze=%s voice=%s gesture=%s",
            job_id,
            score_items.get("POSTURE"),
            score_items.get("EXPRESSION"),
            score_items.get("EYE_CONTACT"),
            score_items.get("VOICE"),
            score_items.get("GESTURE"),
        )
        logger.info(
            "[SCORE] 점수 측정이 끝났습니다. jobId=%s totalScore=%.1f elapsed=%.2fs",
            job_id,
            self._safe_float(result.get("totalScore"), 0.0),
            score_seconds,
        )
        self._merge_visual_score_metrics(metrics, result)
        total_seconds = time.perf_counter() - total_started
        performance = {
            "videoDurationSeconds": round(float(info.durationSeconds), 3),
            "probeSeconds": round(probe_seconds, 3),
            "textAnalysisSeconds": round(text_seconds, 3),
            "audioAnalysisSeconds": round(audio_seconds, 3),
            "visionAnalysisSeconds": round(vision_seconds, 3),
            "gestureAnalysisSeconds": round(gesture_seconds, 3),
            "scoreCalculationSeconds": round(score_seconds, 3),
            "totalAnalysisSeconds": round(total_seconds, 3),
            "realTimeFactor": round(total_seconds / info.durationSeconds, 3) if info.durationSeconds > 0 else None,
        }
        metrics["analysisPerformance"] = performance
        logger.info(
            "Analysis performance: jobId=%s videoDurationSeconds=%.3f textSeconds=%.3f "
            "audioSeconds=%.3f visionSeconds=%.3f gestureSeconds=%.3f scoreSeconds=%.3f "
            "totalSeconds=%.3f realTimeFactor=%s",
            job_id,
            info.durationSeconds,
            text_seconds,
            audio_seconds,
            vision_seconds,
            gesture_seconds,
            score_seconds,
            total_seconds,
            performance["realTimeFactor"],
        )
        self._log_audio_metrics(job_id, info.durationSeconds, metrics)
        self._log_face_detection(job_id, metrics)
        self._log_visual_score_reliability(job_id, metrics)
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
    def _merge_visual_score_metrics(metrics: dict, result: dict) -> None:
        for item in result.get("scores", []):
            category = item.get("category")
            if category not in {"EXPRESSION", "EYE_CONTACT"}:
                continue
            score_metrics = item.get("metrics") or {}
            keys = (
                "expressionRawScore",
                "expressionConfidence",
                "expressionDetectionConfidence",
                "expressionConfidenceCap",
                "expressionLowConfidence",
                "expressionAnalysisReliable",
                "expressionReliabilityLabel",
                "expressionConfidenceReason",
                "expressionFallback",
                "expressionFallbackUsed",
            ) if category == "EXPRESSION" else (
                "eyeContactRawScore",
                "gazeConfidenceCap",
                "gazeDetectionConfidence",
                "gazeLowConfidence",
                "gazeAnalysisReliable",
                "gazeReliabilityLabel",
                "gazeConfidenceReason",
                "gazeFallbackUsed",
            )
            for key in keys:
                if key in score_metrics:
                    metrics[key] = score_metrics[key]
            if category == "EXPRESSION":
                metrics["expressionScore"] = item.get("score")
            else:
                metrics["eyeContactScore"] = item.get("score")

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
            logger.warning(
                "[AUDIO] 음성 지표 fallback을 사용합니다. jobId=%s reason=%s",
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
            "cropRetryDetected=%s totalDetectedFrames=%s faceDetectionRatio=%.3f "
            "faceBoxRatioMin=%s faceBoxRatioAverage=%s faceBoxRatioMax=%s "
            "readFailures=%s rotationDegrees=%s orientationAutoEnabled=%s fallbackUsed=%s",
            job_id,
            attempted,
            metrics.get("faceFullFrameDetectedCount"),
            metrics.get("faceCropRetryAttemptedCount"),
            metrics.get("faceCropRetryDetectedCount"),
            detected,
            BasicAnalyzer._safe_float(metrics.get("faceDetectionRatio"), 0.0),
            metrics.get("faceBoxRatioMin"),
            metrics.get("faceBoxRatioAverage"),
            metrics.get("faceBoxRatioMax"),
            metrics.get("visionReadFailureCount"),
            metrics.get("videoRotationDegrees"),
            metrics.get("videoOrientationAutoEnabled"),
            detected == 0,
        )
        if detected == 0:
            logger.info(
                "Face analysis fallback: jobId=%s reason=face_not_detected attemptedFrames=%s detectedFrames=0",
                job_id,
                attempted,
            )

    @staticmethod
    def _log_visual_score_reliability(job_id: int, metrics: dict) -> None:
        logger.info(
            "Visual score reliability: "
            "jobId=%s "
            "attemptedFaceFrames=%s detectedFaceFrames=%s faceDetectionRatio=%.3f "
            "rawExpressionScore=%.1f finalExpressionScore=%.1f "
            "attemptedGazeFrames=%s detectedGazeFrames=%s gazeDetectionRatio=%.3f "
            "eyeContactRatio=%.3f rawEyeContactScore=%.1f finalEyeContactScore=%.1f "
            "expressionLowConfidence=%s gazeLowConfidence=%s",
            job_id,
            metrics.get("faceAttemptedFrameCount"),
            metrics.get("faceDetectedFrameCount"),
            BasicAnalyzer._safe_float(metrics.get("faceDetectionRatio"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("expressionRawScore"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("expressionScore"), 0.0),
            metrics.get("gazeAttemptedFrameCount"),
            metrics.get("gazeDetectedFrameCount"),
            BasicAnalyzer._safe_float(metrics.get("gazeDetectionRatio"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("eyeContactRatio"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("eyeContactRawScore"), 0.0),
            BasicAnalyzer._safe_float(metrics.get("eyeContactScore"), 0.0),
            metrics.get("expressionLowConfidence"),
            metrics.get("gazeLowConfidence"),
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
