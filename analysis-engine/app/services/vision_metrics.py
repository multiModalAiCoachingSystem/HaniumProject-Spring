from __future__ import annotations

import logging
import math
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from statistics import mean
from typing import Any


logger = logging.getLogger("uvicorn.error")


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


FACE_DETECTION_CONFIDENCE = _env_float("FACE_DETECTION_CONFIDENCE", 0.3)
FACE_TRACKING_CONFIDENCE = _env_float("FACE_TRACKING_CONFIDENCE", 0.3)
FACE_ANALYSIS_MAX_WIDTH = _env_int("FACE_ANALYSIS_MAX_WIDTH", 1280)
FACE_CROP_RETRY_ENABLED = _env_bool("FACE_CROP_RETRY_ENABLED", True)
FACE_CROP_PADDING_RATIO = _env_float("FACE_CROP_PADDING_RATIO", 0.25)
FACE_CROP_MIN_SIZE = _env_int("FACE_CROP_MIN_SIZE", 256)
GAZE_DIAGNOSTIC_FRAME_LIMIT = max(0, _env_int("GAZE_DIAGNOSTIC_FRAME_LIMIT", 20))

GAZE_MAX_YAW_DEGREES = 15.0
GAZE_MAX_PITCH_DEGREES = 10.0
GAZE_NEUTRAL_NOSE_VERTICAL_RATIO = 0.90
GAZE_IRIS_HORIZONTAL_RANGE = (0.32, 0.68)
GAZE_IRIS_VERTICAL_RANGE = (0.20, 0.80)


FALLBACK_METRICS = {
    "shoulderTiltDegrees": 6.0,
    "headMovementRatio": 0.12,
    "postureStabilityRatio": 0.80,
    "eyeContactRatio": 0.68,
    "gazeAwayRatio": 0.32,
    "naturalExpressionRatio": 0.74,
    "tenseExpressionRatio": 0.12,
    "smileRatio": 0.18,
    "emotionRatio": {
        "happy": 0.22,
        "neutral": 0.64,
        "anxious": 0.14,
    },
    "visionFrameCount": 0,
    "poseDetectionRatio": 0.0,
    "faceDetectionRatio": 0.0,
    "faceAttemptedFrameCount": 0,
    "faceDetectedFrameCount": 0,
    "gazeAttemptedFrameCount": 0,
    "gazeDetectedFrameCount": 0,
    "gazeDetectionRatio": 0.0,
    "eyeContactFrameCount": 0,
    "visionAnalysisFallback": True,
}


# 함수 역할: 이후 점수 계산 단계에서 안정적인 값을 사용하도록 숫자 비율을 지정된 범위 안으로 제한합니다.
def _clamp(value: float, minimum: float = 0.0, maximum: float = 1.0) -> float:
    return max(minimum, min(maximum, value))


# 함수 역할: 실제 분석이 실행될 때만 OpenCV와 MediaPipe를 안전하게 불러옵니다.
def _load_vision_dependencies() -> tuple[Any, Any]:
    try:
        import cv2  # type: ignore
        import mediapipe as mp  # type: ignore
    except Exception:
        return None, None
    if not hasattr(mp, "solutions"):
        return None, None
    if not hasattr(mp.solutions, "pose") or not hasattr(mp.solutions, "face_mesh"):
        return None, None
    return cv2, mp


def mediapipe_capabilities() -> dict[str, Any]:
    try:
        import mediapipe as mp  # type: ignore
    except Exception as exception:
        return {
            "version": "unavailable",
            "solutionsAvailable": False,
            "poseAvailable": False,
            "faceMeshAvailable": False,
            "handsAvailable": False,
            "error": exception.__class__.__name__,
        }

    solutions = getattr(mp, "solutions", None)
    return {
        "version": getattr(mp, "__version__", "unknown"),
        "solutionsAvailable": solutions is not None,
        "poseAvailable": hasattr(solutions, "pose") if solutions is not None else False,
        "faceMeshAvailable": hasattr(solutions, "face_mesh") if solutions is not None else False,
        "handsAvailable": hasattr(solutions, "hands") if solutions is not None else False,
        "error": None,
    }


# 함수 역할: 랜드마크 좌표를 정규화된 x, y 좌표 쌍으로 반환합니다.
def _point(landmarks: Any, index: int) -> tuple[float, float]:
    landmark = landmarks[index]
    return float(landmark.x), float(landmark.y)


@dataclass(frozen=True)
class GazeMeasurement:
    yaw_degrees: float
    pitch_degrees: float
    eye_horizontal_ratio: float | None
    eye_vertical_ratio: float | None
    iris_used: bool
    looking_at_camera: bool


def _normalized_position(value: float, first: float, second: float) -> float | None:
    lower, upper = sorted((first, second))
    span = upper - lower
    if not all(math.isfinite(item) for item in (value, lower, upper)) or span < 1e-5:
        return None
    return (value - lower) / span


def _iris_eye_ratio(
    landmarks: Any,
    iris_indices: tuple[int, ...],
    corner_indices: tuple[int, int],
    eyelid_indices: tuple[int, int],
) -> tuple[float, float] | None:
    if len(landmarks) <= max(*iris_indices, *corner_indices, *eyelid_indices):
        return None
    iris_x = mean(float(landmarks[index].x) for index in iris_indices)
    iris_y = mean(float(landmarks[index].y) for index in iris_indices)
    horizontal = _normalized_position(
        iris_x,
        float(landmarks[corner_indices[0]].x),
        float(landmarks[corner_indices[1]].x),
    )
    vertical = _normalized_position(
        iris_y,
        float(landmarks[eyelid_indices[0]].y),
        float(landmarks[eyelid_indices[1]].y),
    )
    if horizontal is None or vertical is None:
        return None
    if not (-0.5 <= horizontal <= 1.5 and -0.5 <= vertical <= 1.5):
        return None
    return horizontal, vertical


def _is_looking_at_camera(
    yaw_degrees: float,
    pitch_degrees: float,
    eye_horizontal_ratio: float | None,
    eye_vertical_ratio: float | None,
) -> bool:
    head_forward = (
        math.isfinite(yaw_degrees)
        and math.isfinite(pitch_degrees)
        and abs(yaw_degrees) <= GAZE_MAX_YAW_DEGREES
        and abs(pitch_degrees) <= GAZE_MAX_PITCH_DEGREES
    )
    if not head_forward:
        return False
    if eye_horizontal_ratio is None or eye_vertical_ratio is None:
        return True
    return (
        GAZE_IRIS_HORIZONTAL_RANGE[0] <= eye_horizontal_ratio <= GAZE_IRIS_HORIZONTAL_RANGE[1]
        and GAZE_IRIS_VERTICAL_RANGE[0] <= eye_vertical_ratio <= GAZE_IRIS_VERTICAL_RANGE[1]
    )


def _measure_gaze(face_landmarks: Any) -> GazeMeasurement | None:
    landmarks = getattr(face_landmarks, "landmark", None)
    if landmarks is None or len(landmarks) <= 263:
        return None
    try:
        left_eye = _point(landmarks, 33)
        right_eye = _point(landmarks, 263)
        nose = _point(landmarks, 1)
        eye_center_x = (left_eye[0] + right_eye[0]) / 2.0
        eye_center_y = (left_eye[1] + right_eye[1]) / 2.0
        face_width = abs(right_eye[0] - left_eye[0])
        if face_width < 0.02:
            return None

        horizontal_offset = (nose[0] - eye_center_x) / face_width
        vertical_offset = (nose[1] - eye_center_y) / face_width
        yaw_degrees = math.degrees(math.atan(horizontal_offset))
        pitch_degrees = math.degrees(math.atan(vertical_offset - GAZE_NEUTRAL_NOSE_VERTICAL_RATIO))

        right_iris = _iris_eye_ratio(landmarks, (468, 469, 470, 471, 472), (33, 133), (159, 145))
        left_iris = _iris_eye_ratio(landmarks, (473, 474, 475, 476, 477), (362, 263), (386, 374))
        iris_ratios = [ratio for ratio in (right_iris, left_iris) if ratio is not None]
        eye_horizontal_ratio = mean(item[0] for item in iris_ratios) if iris_ratios else None
        eye_vertical_ratio = mean(item[1] for item in iris_ratios) if iris_ratios else None
        looking_at_camera = _is_looking_at_camera(
            yaw_degrees,
            pitch_degrees,
            eye_horizontal_ratio,
            eye_vertical_ratio,
        )
        return GazeMeasurement(
            yaw_degrees=yaw_degrees,
            pitch_degrees=pitch_degrees,
            eye_horizontal_ratio=eye_horizontal_ratio,
            eye_vertical_ratio=eye_vertical_ratio,
            iris_used=bool(iris_ratios),
            looking_at_camera=looking_at_camera,
        )
    except (IndexError, TypeError, ValueError, AttributeError):
        return None


# 함수 역할: MediaPipe 자세 랜드마크를 이용해 어깨선의 기울기를 각도 단위로 계산합니다.
def _shoulder_tilt(pose_landmarks: Any) -> float | None:
    left = pose_landmarks.landmark[11]
    right = pose_landmarks.landmark[12]
    if getattr(left, "visibility", 1.0) < 0.45 or getattr(right, "visibility", 1.0) < 0.45:
        return None
    dx = abs(float(right.x) - float(left.x))
    dy = abs(float(right.y) - float(left.y))
    if dx < 0.001:
        return None
    return math.degrees(math.atan2(dy, dx))


# 함수 역할: 얼굴이 카메라 방향을 향하고 있는지 추정합니다.
def _is_forward_gaze(face_landmarks: Any) -> bool:
    measurement = _measure_gaze(face_landmarks)
    return measurement.looking_at_camera if measurement is not None else False


# 함수 역할: 간단한 표정 판단 신호로 사용할 입 모양 비율을 계산합니다.
def _mouth_ratios(face_landmarks: Any) -> tuple[float, float] | None:
    landmarks = face_landmarks.landmark
    left_face = _point(landmarks, 234)
    right_face = _point(landmarks, 454)
    left_mouth = _point(landmarks, 61)
    right_mouth = _point(landmarks, 291)
    upper_lip = _point(landmarks, 13)
    lower_lip = _point(landmarks, 14)

    face_width = abs(right_face[0] - left_face[0])
    if face_width < 0.02:
        return None
    mouth_width = abs(right_mouth[0] - left_mouth[0]) / face_width
    mouth_open = abs(lower_lip[1] - upper_lip[1]) / face_width
    return mouth_width, mouth_open


def _resize_bgr_for_face(cv2: Any, frame: Any) -> tuple[Any, int, int]:
    height, width = frame.shape[:2]
    if width <= 0 or height <= 0:
        return frame, width, height
    if FACE_ANALYSIS_MAX_WIDTH <= 0 or width <= FACE_ANALYSIS_MAX_WIDTH:
        return frame, width, height
    scale = FACE_ANALYSIS_MAX_WIDTH / float(width)
    resized = cv2.resize(frame, (FACE_ANALYSIS_MAX_WIDTH, max(1, int(height * scale))), interpolation=cv2.INTER_AREA)
    next_height, next_width = resized.shape[:2]
    return resized, next_width, next_height


def _face_cascade(cv2: Any) -> Any | None:
    cascade_path = Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"
    cascade = cv2.CascadeClassifier(str(cascade_path))
    if cascade.empty():
        return None
    return cascade


def _detect_face_boxes(cv2: Any, frame_bgr: Any) -> list[tuple[int, int, int, int]]:
    cascade = _face_cascade(cv2)
    if cascade is None:
        return []
    gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)
    boxes = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(40, 40))
    return sorted((tuple(map(int, box)) for box in boxes), key=lambda box: box[2] * box[3], reverse=True)


def _crop_with_padding(frame_bgr: Any, box: tuple[int, int, int, int]) -> Any | None:
    height, width = frame_bgr.shape[:2]
    x, y, w, h = box
    padding = int(max(w, h) * FACE_CROP_PADDING_RATIO)
    left = max(0, x - padding)
    top = max(0, y - padding)
    right = min(width, x + w + padding)
    bottom = min(height, y + h + padding)
    if right <= left or bottom <= top:
        return None
    return frame_bgr[top:bottom, left:right]


def _prepare_face_crop(cv2: Any, crop_bgr: Any) -> Any:
    height, width = crop_bgr.shape[:2]
    shortest = min(height, width)
    if shortest >= FACE_CROP_MIN_SIZE:
        return crop_bgr
    scale = FACE_CROP_MIN_SIZE / max(shortest, 1)
    return cv2.resize(crop_bgr, (max(1, int(width * scale)), max(1, int(height * scale))), interpolation=cv2.INTER_CUBIC)


def _face_landmark_box_ratio(face_landmarks: Any) -> float | None:
    points = [
        (float(landmark.x), float(landmark.y))
        for landmark in face_landmarks.landmark
        if math.isfinite(float(landmark.x)) and math.isfinite(float(landmark.y))
    ]
    if not points:
        return None
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    width = _clamp(max(xs) - min(xs))
    height = _clamp(max(ys) - min(ys))
    return _clamp(width * height)


def _face_box_ratio(frame_bgr: Any, box: tuple[int, int, int, int]) -> float | None:
    height, width = frame_bgr.shape[:2]
    if width <= 0 or height <= 0:
        return None
    _, _, box_width, box_height = box
    return _clamp((box_width * box_height) / float(width * height))


def _create_face_detector(mp: Any) -> Any:
    # Frames are sampled with random seeks, so each frame must be detected independently.
    return mp.solutions.face_mesh.FaceMesh(
        static_image_mode=True,
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=FACE_DETECTION_CONFIDENCE,
        min_tracking_confidence=FACE_TRACKING_CONFIDENCE,
    )


def _enable_auto_orientation(cv2: Any, capture: Any) -> bool:
    property_id = getattr(cv2, "CAP_PROP_ORIENTATION_AUTO", None)
    if property_id is None:
        return False
    try:
        return bool(capture.set(property_id, 1))
    except Exception:
        return False


def _detect_face_landmarks(
    cv2: Any,
    face_detector: Any,
    frame_bgr: Any,
    frame_rgb: Any,
) -> tuple[Any | None, bool, bool, float | None]:
    full_result = face_detector.process(frame_rgb)
    if full_result.multi_face_landmarks:
        landmarks = full_result.multi_face_landmarks[0]
        return landmarks, False, False, _face_landmark_box_ratio(landmarks)

    if not FACE_CROP_RETRY_ENABLED:
        return None, False, False, None

    boxes = _detect_face_boxes(cv2, frame_bgr)
    if not boxes:
        return None, False, False, None

    for box in boxes[:3]:
        crop = _crop_with_padding(frame_bgr, box)
        if crop is None:
            continue
        crop = _prepare_face_crop(cv2, crop)
        crop_rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB)
        crop_result = face_detector.process(crop_rgb)
        if crop_result.multi_face_landmarks:
            return crop_result.multi_face_landmarks[0], True, True, _face_box_ratio(frame_bgr, box)

    return None, True, False, None


# 함수 역할: 프레임 분석을 실행할 수 없을 때 사용할 일관된 대체값을 생성합니다.
def _fallback(duration_seconds: float, reason: str) -> dict:
    long_talk_adjustment = min(0.08, max(0.0, (duration_seconds - 180.0) / 1800.0))
    eye_ratio = 0.68 if duration_seconds < 300 else 0.62
    payload = dict(FALLBACK_METRICS)
    payload["emotionRatio"] = dict(FALLBACK_METRICS["emotionRatio"])
    payload.update(
        {
            "headMovementRatio": round(0.12 + long_talk_adjustment, 3),
            "postureStabilityRatio": round(max(0.65, 0.80 - long_talk_adjustment), 3),
            "eyeContactRatio": eye_ratio,
            "gazeAwayRatio": round(1.0 - eye_ratio, 3),
            "tenseExpressionRatio": 0.12 if duration_seconds < 300 else 0.16,
            "visionAnalysisFallbackReason": reason,
        }
    )
    return payload


# 함수 역할: 영상 프레임을 일정 간격으로 추출하고 자세, 시선, 표정 지표를 계산합니다.
@lru_cache(maxsize=8)
def analyze_vision_metrics(file_path: str | None, duration_seconds: float) -> dict:
    if not file_path or not Path(file_path).exists():
        return _fallback(duration_seconds, "video_file_missing")

    cv2, mp = _load_vision_dependencies()
    if cv2 is None or mp is None:
        return _fallback(duration_seconds, "vision_dependencies_missing")

    cap = cv2.VideoCapture(file_path)
    if not cap.isOpened():
        return _fallback(duration_seconds, "video_open_failed")
    orientation_auto_enabled = _enable_auto_orientation(cv2, cap)

    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    if frame_count <= 0:
        cap.release()
        return _fallback(duration_seconds, "frame_count_missing")

    max_samples = 240
    target_sample_rate = 2.0
    step = max(1, int(fps / target_sample_rate))
    if frame_count // step > max_samples:
        step = max(step, frame_count // max_samples)

    pose_detector = mp.solutions.pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.45,
        min_tracking_confidence=0.45,
    )
    face_detector = _create_face_detector(mp)

    read_attempted = 0
    read_failed = 0
    sampled = 0
    pose_detected = 0
    face_detected = 0
    gaze_detected = 0
    face_attempted = 0
    full_frame_detected = 0
    crop_retry_attempted = 0
    crop_retry_detected = 0
    forward_gaze = 0
    gaze_yaw_degrees: list[float] = []
    gaze_pitch_degrees: list[float] = []
    gaze_eye_horizontal_ratios: list[float] = []
    gaze_eye_vertical_ratios: list[float] = []
    gaze_iris_frames = 0
    gaze_diagnostic_frames = 0
    stable_pose = 0
    smile_frames = 0
    tense_frames = 0
    natural_frames = 0
    shoulder_tilts: list[float] = []
    nose_positions: list[tuple[float, float]] = []
    movements: list[float] = []
    face_box_ratios: list[float] = []
    face_input_width = 0
    face_input_height = 0
    first_frame_index: int | None = None
    first_frame_brightness: float | None = None
    orientation_meta = 0.0
    orientation_property = getattr(cv2, "CAP_PROP_ORIENTATION_META", None)
    if orientation_property is not None:
        orientation_meta = float(cap.get(orientation_property) or 0.0)

    try:
        for frame_index in range(0, frame_count, step):
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            read_attempted += 1
            ok, frame = cap.read()
            if not ok:
                read_failed += 1
                continue

            sampled += 1
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            pose_result = pose_detector.process(rgb)
            face_frame, face_input_width, face_input_height = _resize_bgr_for_face(cv2, frame)
            if first_frame_index is None:
                first_frame_index = frame_index
                first_frame_brightness = float(face_frame.mean())
            face_rgb = cv2.cvtColor(face_frame, cv2.COLOR_BGR2RGB)
            face_attempted += 1
            face_landmarks, crop_attempted, crop_detected, face_box_ratio = _detect_face_landmarks(
                cv2,
                face_detector,
                face_frame,
                face_rgb,
            )
            if crop_attempted:
                crop_retry_attempted += 1
            if crop_detected:
                crop_retry_detected += 1
            if face_box_ratio is not None:
                face_box_ratios.append(face_box_ratio)

            tilt: float | None = None
            movement = 0.0
            if pose_result.pose_landmarks:
                pose_detected += 1
                tilt = _shoulder_tilt(pose_result.pose_landmarks)
                nose = pose_result.pose_landmarks.landmark[0]
                current_nose = (float(nose.x), float(nose.y))
                if nose_positions:
                    previous = nose_positions[-1]
                    movement = math.dist(previous, current_nose)
                    movements.append(movement)
                nose_positions.append(current_nose)
                if tilt is not None:
                    shoulder_tilts.append(tilt)
                    if tilt <= 10.0 and movement <= 0.035:
                        stable_pose += 1

            if face_landmarks:
                face_detected += 1
                if not crop_detected:
                    full_frame_detected += 1
                gaze = _measure_gaze(face_landmarks)
                if gaze is not None:
                    gaze_detected += 1
                    gaze_yaw_degrees.append(gaze.yaw_degrees)
                    gaze_pitch_degrees.append(gaze.pitch_degrees)
                    if gaze.eye_horizontal_ratio is not None:
                        gaze_eye_horizontal_ratios.append(gaze.eye_horizontal_ratio)
                    if gaze.eye_vertical_ratio is not None:
                        gaze_eye_vertical_ratios.append(gaze.eye_vertical_ratio)
                    if gaze.iris_used:
                        gaze_iris_frames += 1
                    if gaze.looking_at_camera:
                        forward_gaze += 1
                    if gaze_diagnostic_frames < GAZE_DIAGNOSTIC_FRAME_LIMIT:
                        logger.debug(
                            "Gaze frame: frame=%s faceDetected=true landmarksDetected=true "
                            "yaw=%.2f pitch=%.2f roll=not_used eyeHorizontalRatio=%s "
                            "eyeVerticalRatio=%s isLookingAtCamera=%s",
                            frame_index,
                            gaze.yaw_degrees,
                            gaze.pitch_degrees,
                            f"{gaze.eye_horizontal_ratio:.3f}" if gaze.eye_horizontal_ratio is not None else "not_available",
                            f"{gaze.eye_vertical_ratio:.3f}" if gaze.eye_vertical_ratio is not None else "not_available",
                            gaze.looking_at_camera,
                        )
                        gaze_diagnostic_frames += 1

                mouth = _mouth_ratios(face_landmarks)
                if mouth:
                    mouth_width, mouth_open = mouth
                    is_smile = mouth_width >= 0.36 and mouth_open <= 0.12
                    is_tense = mouth_width <= 0.25 or mouth_open >= 0.18
                    if is_smile:
                        smile_frames += 1
                    if is_tense:
                        tense_frames += 1
                    if not is_tense:
                        natural_frames += 1

            if sampled >= max_samples:
                break
    except Exception as exception:
        logger.warning("Vision analysis fallback. reason=mediapipe_runtime_error message=%s", exception)
        return _fallback(duration_seconds, "mediapipe_runtime_error")
    finally:
        cap.release()
        pose_detector.close()
        face_detector.close()

    if sampled == 0 or pose_detected == 0 and face_detected == 0:
        return _fallback(duration_seconds, "no_detectable_frames")

    if first_frame_index is not None:
        logger.info(
            "Face analysis frame: frameIndex=%s width=%s height=%s meanBrightness=%.1f",
            first_frame_index,
            face_input_width,
            face_input_height,
            first_frame_brightness or 0.0,
        )
    logger.info(
        "Face detection summary: attemptedFrames=%s fullFrameDetected=%s cropRetryAttempted=%s "
        "cropRetryDetected=%s totalDetectedFrames=%s faceDetectionRatio=%.3f fallbackUsed=%s",
        face_attempted,
        full_frame_detected,
        crop_retry_attempted,
        crop_retry_detected,
        face_detected,
        face_detected / face_attempted if face_attempted else 0.0,
        face_detected == 0,
    )
    if face_attempted > 0 and face_detected == 0:
        logger.info(
            "Face analysis fallback: reason=face_not_detected attemptedFrames=%s detectedFrames=0",
            face_attempted,
        )

    avg_tilt = mean(shoulder_tilts) if shoulder_tilts else FALLBACK_METRICS["shoulderTiltDegrees"]
    avg_movement = mean(movements) if movements else 0.0
    head_movement_ratio = _clamp(avg_movement / 0.08)
    posture_stability_ratio = stable_pose / pose_detected if pose_detected else FALLBACK_METRICS["postureStabilityRatio"]
    eye_contact_ratio = forward_gaze / gaze_detected if gaze_detected else FALLBACK_METRICS["eyeContactRatio"]
    tense_ratio = tense_frames / face_detected if face_detected else FALLBACK_METRICS["tenseExpressionRatio"]
    smile_ratio = smile_frames / face_detected if face_detected else FALLBACK_METRICS["smileRatio"]
    natural_ratio = natural_frames / face_detected if face_detected else FALLBACK_METRICS["naturalExpressionRatio"]

    happy_ratio = _clamp(smile_ratio)
    anxious_ratio = _clamp(tense_ratio)
    neutral_ratio = _clamp(1.0 - happy_ratio - anxious_ratio)

    logger.info(
        "Gaze detection summary: attemptedFrames=%s detectedFrames=%s eyeContactFrames=%s "
        "eyeContactRatio=%.3f yawRange=%s pitchRange=%s irisUsedRatio=%.3f fallbackUsed=%s",
        face_attempted,
        gaze_detected,
        forward_gaze,
        eye_contact_ratio,
        (
            f"{min(gaze_yaw_degrees):.2f}..{max(gaze_yaw_degrees):.2f}"
            if gaze_yaw_degrees
            else "not_available"
        ),
        (
            f"{min(gaze_pitch_degrees):.2f}..{max(gaze_pitch_degrees):.2f}"
            if gaze_pitch_degrees
            else "not_available"
        ),
        gaze_iris_frames / gaze_detected if gaze_detected else 0.0,
        gaze_detected == 0,
    )

    return {
        "shoulderTiltDegrees": round(float(avg_tilt), 2),
        "headMovementRatio": round(float(head_movement_ratio), 3),
        "postureStabilityRatio": round(float(_clamp(posture_stability_ratio)), 3),
        "eyeContactRatio": round(float(_clamp(eye_contact_ratio)), 3),
        "gazeAwayRatio": round(float(_clamp(1.0 - eye_contact_ratio)), 3),
        "naturalExpressionRatio": round(float(_clamp(natural_ratio)), 3),
        "tenseExpressionRatio": round(float(_clamp(tense_ratio)), 3),
        "smileRatio": round(float(_clamp(smile_ratio)), 3),
        "emotionRatio": {
            "happy": round(happy_ratio, 3),
            "neutral": round(neutral_ratio, 3),
            "anxious": round(anxious_ratio, 3),
        },
        "visionFrameCount": sampled,
        "visionAttemptedFrameCount": read_attempted,
        "visionReadFailureCount": read_failed,
        "poseDetectionRatio": round(pose_detected / sampled, 3),
        "faceDetectionRatio": round(face_detected / face_attempted, 3) if face_attempted else 0.0,
        "faceAttemptedFrameCount": face_attempted,
        "faceFullFrameDetectedCount": full_frame_detected,
        "faceCropRetryAttemptedCount": crop_retry_attempted,
        "faceCropRetryDetectedCount": crop_retry_detected,
        "faceDetectedFrameCount": face_detected,
        "faceBoxRatioMin": round(min(face_box_ratios), 4) if face_box_ratios else None,
        "faceBoxRatioAverage": round(mean(face_box_ratios), 4) if face_box_ratios else None,
        "faceBoxRatioMax": round(max(face_box_ratios), 4) if face_box_ratios else None,
        # 시선 비율의 분모는 얼굴 프레임이 아니라 실제 gaze geometry가 유효한 프레임이다.
        "gazeAttemptedFrameCount": face_attempted,
        "gazeDetectedFrameCount": gaze_detected,
        "gazeDetectionRatio": round(gaze_detected / face_attempted, 3) if face_attempted else 0.0,
        "eyeContactFrameCount": forward_gaze,
        "gazeYawDegreesAverage": round(mean(gaze_yaw_degrees), 2) if gaze_yaw_degrees else None,
        "gazePitchDegreesAverage": round(mean(gaze_pitch_degrees), 2) if gaze_pitch_degrees else None,
        "gazeEyeHorizontalRatioAverage": round(mean(gaze_eye_horizontal_ratios), 3) if gaze_eye_horizontal_ratios else None,
        "gazeEyeVerticalRatioAverage": round(mean(gaze_eye_vertical_ratios), 3) if gaze_eye_vertical_ratios else None,
        "gazeIrisUsedRatio": round(gaze_iris_frames / gaze_detected, 3) if gaze_detected else 0.0,
        "faceInputWidth": face_input_width,
        "faceInputHeight": face_input_height,
        "videoRotationDegrees": round(orientation_meta, 1),
        "videoOrientationAutoEnabled": orientation_auto_enabled,
        "faceCropRetryEnabled": FACE_CROP_RETRY_ENABLED,
        "visionAnalysisFallback": False,
    }
