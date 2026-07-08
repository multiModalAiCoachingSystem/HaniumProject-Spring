from __future__ import annotations

import logging
import math
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any

from app.services.vision_metrics import _clamp, _load_vision_dependencies


log = logging.getLogger(__name__)
GESTURE_MAX_SAMPLES = 240


POSE_LANDMARKS = {
    "left_shoulder": 11,
    "right_shoulder": 12,
    "left_elbow": 13,
    "right_elbow": 14,
    "left_wrist": 15,
    "right_wrist": 16,
}


FALLBACK_GESTURE_METRICS = {
    "gestureMovementRatio": 0.46,
    "gestureCountPerMinute": 4.2,
    "stillHandRatio": 0.18,
    "gestureActivityScore": 72.0,
    "gestureAverageMovement": 0.0,
    "gestureFrequency": 4.2,
    "gestureBalance": 0.82,
    "gestureStability": 0.78,
    "gestureVisibleRatio": 0.0,
    "gestureValidFrameCount": 0,
    "gestureSampledFrameCount": 0,
    "gestureAnalysisFallback": True,
}


@dataclass(frozen=True)
class ArmPoints:
    left_elbow: tuple[float, float]
    right_elbow: tuple[float, float]
    left_wrist: tuple[float, float]
    right_wrist: tuple[float, float]
    shoulder_width: float


class GestureAnalyzer:
    min_visibility = 0.45
    min_valid_frames = 8
    min_visible_ratio = 0.20

    def analyze(self, duration_seconds: float, file_path: str | None) -> dict[str, Any]:
        if not file_path or not Path(file_path).exists():
            return self._fallback(duration_seconds, "video_file_missing")

        cv2, mp = _load_vision_dependencies()
        if cv2 is None or mp is None:
            return self._fallback(duration_seconds, "vision_dependencies_missing")

        cap = cv2.VideoCapture(file_path)
        if not cap.isOpened():
            return self._fallback(duration_seconds, "video_open_failed")

        try:
            fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
            frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
            if frame_count <= 0:
                return self._fallback(duration_seconds, "frame_count_missing")

            max_samples = GESTURE_MAX_SAMPLES
            step = max(1, int(fps / 3.0))
            if frame_count // step > max_samples:
                step = max(step, frame_count // max_samples)

            pose_detector = mp.solutions.pose.Pose(
                static_image_mode=False,
                model_complexity=1,
                enable_segmentation=False,
                min_detection_confidence=0.45,
                min_tracking_confidence=0.45,
            )
            try:
                return self._analyze_frames(cap, cv2, pose_detector, frame_count, step, duration_seconds, max_samples)
            finally:
                pose_detector.close()
        except Exception as exception:
            log.warning("Gesture analysis fallback. reason=exception message=%s", exception)
            return self._fallback(duration_seconds, "gesture_analysis_error")
        finally:
            cap.release()

    def _analyze_frames(
        self,
        cap: Any,
        cv2: Any,
        pose_detector: Any,
        frame_count: int,
        step: int,
        duration_seconds: float,
        max_samples: int,
    ) -> dict[str, Any]:
        sampled = 0
        valid_frames = 0
        previous: ArmPoints | None = None
        movements: list[float] = []
        raw_movements: list[float] = []
        left_movements: list[float] = []
        right_movements: list[float] = []
        active_frames = 0
        outlier_frames = 0
        still_frames = 0

        for frame_index in range(0, frame_count, step):
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, frame = cap.read()
            if not ok:
                continue

            sampled += 1
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            pose_result = pose_detector.process(rgb)
            if pose_result.pose_landmarks:
                points = self._extract_arm_points(pose_result.pose_landmarks)
                if points is not None:
                    valid_frames += 1
                    if previous is not None:
                        left, right, movement = self._movement(previous, points)
                        raw_movements.append(movement)
                        if self._is_valid_movement(movement):
                            movements.append(movement)
                            left_movements.append(left)
                            right_movements.append(right)
                            if movement >= 0.06:
                                active_frames += 1
                            if movement < 0.015:
                                still_frames += 1
                        else:
                            outlier_frames += 1
                    previous = points

            if sampled >= max_samples:
                break

        visible_ratio = valid_frames / sampled if sampled else 0.0
        if sampled < self.min_valid_frames or valid_frames < self.min_valid_frames:
            return self._fallback(duration_seconds, "insufficient_gesture_frames", sampled, valid_frames, visible_ratio)
        if visible_ratio < self.min_visible_ratio:
            return self._fallback(duration_seconds, "low_gesture_visible_ratio", sampled, valid_frames, visible_ratio)
        if not movements:
            return self._fallback(duration_seconds, "insufficient_gesture_motion", sampled, valid_frames, visible_ratio)

        metrics = self._metrics_from_movements(
            duration_seconds,
            sampled,
            valid_frames,
            visible_ratio,
            movements,
            raw_movements,
            left_movements,
            right_movements,
            active_frames,
            outlier_frames,
            still_frames,
        )
        log.info(
            "Gesture analysis completed. sampled=%s valid=%s visible_ratio=%.3f avg_movement=%.4f fallback=false score=%.1f",
            sampled,
            valid_frames,
            visible_ratio,
            metrics["gestureAverageMovement"],
            metrics["gestureActivityScore"],
        )
        return metrics

    def _extract_arm_points(self, pose_landmarks: Any) -> ArmPoints | None:
        landmarks = pose_landmarks.landmark
        for index in POSE_LANDMARKS.values():
            landmark = landmarks[index]
            if getattr(landmark, "visibility", 1.0) < self.min_visibility:
                return None
            if not self._normal_point(float(landmark.x), float(landmark.y)):
                return None

        left_shoulder = self._point(landmarks[POSE_LANDMARKS["left_shoulder"]])
        right_shoulder = self._point(landmarks[POSE_LANDMARKS["right_shoulder"]])
        shoulder_width = math.dist(left_shoulder, right_shoulder)
        if shoulder_width < 0.05:
            return None

        return ArmPoints(
            left_elbow=self._point(landmarks[POSE_LANDMARKS["left_elbow"]]),
            right_elbow=self._point(landmarks[POSE_LANDMARKS["right_elbow"]]),
            left_wrist=self._point(landmarks[POSE_LANDMARKS["left_wrist"]]),
            right_wrist=self._point(landmarks[POSE_LANDMARKS["right_wrist"]]),
            shoulder_width=shoulder_width,
        )

    @staticmethod
    def _point(landmark: Any) -> tuple[float, float]:
        return float(landmark.x), float(landmark.y)

    @staticmethod
    def _normal_point(x: float, y: float) -> bool:
        return math.isfinite(x) and math.isfinite(y) and -0.25 <= x <= 1.25 and -0.25 <= y <= 1.25

    def _movement(self, previous: ArmPoints, current: ArmPoints) -> tuple[float, float, float]:
        left = self._side_movement(previous.left_elbow, previous.left_wrist, current.left_elbow, current.left_wrist)
        right = self._side_movement(previous.right_elbow, previous.right_wrist, current.right_elbow, current.right_wrist)
        shoulder_width = max(previous.shoulder_width, current.shoulder_width, 0.05)
        normalized_left = left / shoulder_width
        normalized_right = right / shoulder_width
        return normalized_left, normalized_right, (normalized_left + normalized_right) / 2.0

    @staticmethod
    def _side_movement(
        previous_elbow: tuple[float, float],
        previous_wrist: tuple[float, float],
        current_elbow: tuple[float, float],
        current_wrist: tuple[float, float],
    ) -> float:
        elbow = math.dist(previous_elbow, current_elbow)
        wrist = math.dist(previous_wrist, current_wrist)
        return wrist * 0.65 + elbow * 0.35

    @staticmethod
    def _is_valid_movement(value: float) -> bool:
        return math.isfinite(value) and 0.0 <= value <= 1.25

    def _metrics_from_movements(
        self,
        duration_seconds: float,
        sampled: int,
        valid_frames: int,
        visible_ratio: float,
        movements: list[float],
        raw_movements: list[float],
        left_movements: list[float],
        right_movements: list[float],
        active_frames: int,
        outlier_frames: int,
        still_frames: int,
    ) -> dict[str, Any]:
        avg_movement = mean(movements)
        movement_ratio = _clamp(avg_movement / 0.16)
        minutes = max(duration_seconds / 60.0, valid_frames / 180.0, 1 / 60.0)
        gesture_frequency = active_frames / minutes
        gesture_balance = self._balance_score(sum(left_movements), sum(right_movements))
        outlier_ratio = outlier_frames / max(len(raw_movements), 1)
        gesture_stability = _clamp(1.0 - outlier_ratio * 2.0)
        still_ratio = still_frames / max(len(movements), 1)
        activity_score = self._activity_score(movement_ratio, gesture_frequency, gesture_balance, gesture_stability)

        return {
            "gestureMovementRatio": round(float(movement_ratio), 3),
            "gestureCountPerMinute": round(float(gesture_frequency), 2),
            "stillHandRatio": round(float(_clamp(still_ratio)), 3),
            "gestureActivityScore": round(float(activity_score), 1),
            "gestureAverageMovement": round(float(avg_movement), 4),
            "gestureFrequency": round(float(gesture_frequency), 2),
            "gestureBalance": round(float(gesture_balance), 3),
            "gestureStability": round(float(gesture_stability), 3),
            "gestureVisibleRatio": round(float(visible_ratio), 3),
            "gestureValidFrameCount": valid_frames,
            "gestureSampledFrameCount": sampled,
            "gestureOutlierRatio": round(float(_clamp(outlier_ratio)), 3),
            "gestureAnalysisFallback": False,
        }

    @staticmethod
    def _balance_score(left_total: float, right_total: float) -> float:
        total = left_total + right_total
        if total <= 0.0001:
            return 0.5
        dominance = abs(left_total - right_total) / total
        return _clamp(1.0 - dominance)

    @staticmethod
    def _activity_score(
        movement_ratio: float,
        gesture_frequency: float,
        gesture_balance: float,
        gesture_stability: float,
    ) -> float:
        movement_score = _range_score_0_to_100(movement_ratio, 0.12, 0.35, 0.68, 0.95)
        frequency_score = _range_score_0_to_100(gesture_frequency, 1.0, 3.0, 9.0, 16.0)
        balance_score = 55.0 + gesture_balance * 45.0
        stability_score = 45.0 + gesture_stability * 55.0
        score = movement_score * 0.40 + frequency_score * 0.30 + balance_score * 0.15 + stability_score * 0.15
        return max(0.0, min(100.0, score))

    def _fallback(
        self,
        duration_seconds: float,
        reason: str,
        sampled: int = 0,
        valid_frames: int = 0,
        visible_ratio: float = 0.0,
    ) -> dict[str, Any]:
        payload = dict(FALLBACK_GESTURE_METRICS)
        if duration_seconds >= 300:
            payload["gestureMovementRatio"] = 0.40
        payload.update(
            {
                "gestureFrequency": payload["gestureCountPerMinute"],
                "gestureSampledFrameCount": sampled,
                "gestureValidFrameCount": valid_frames,
                "gestureVisibleRatio": round(float(_clamp(visible_ratio)), 3),
                "gestureAnalysisFallback": True,
                "gestureAnalysisFallbackReason": reason,
            }
        )
        log.info(
            "Gesture analysis fallback. sampled=%s valid=%s visible_ratio=%.3f avg_movement=0.0000 fallback=true reason=%s score=%.1f",
            sampled,
            valid_frames,
            visible_ratio,
            reason,
            payload["gestureActivityScore"],
        )
        return payload


def _range_score_0_to_100(value: float, lower: float, ideal_low: float, ideal_high: float, upper: float) -> float:
    if ideal_low <= value <= ideal_high:
        return 100.0
    if lower <= value < ideal_low:
        return 50.0 + (value - lower) / (ideal_low - lower) * 50.0
    if ideal_high < value <= upper:
        return 100.0 - (value - ideal_high) / (upper - ideal_high) * 45.0
    return 45.0
