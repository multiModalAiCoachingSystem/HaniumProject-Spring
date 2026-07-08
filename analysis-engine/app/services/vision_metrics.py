from __future__ import annotations

import math
from functools import lru_cache
from pathlib import Path
from statistics import mean
from typing import Any


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
    return cv2, mp


# 함수 역할: 랜드마크 좌표를 정규화된 x, y 좌표 쌍으로 반환합니다.
def _point(landmarks: Any, index: int) -> tuple[float, float]:
    landmark = landmarks[index]
    return float(landmark.x), float(landmark.y)


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
    landmarks = face_landmarks.landmark
    left_eye = _point(landmarks, 33)
    right_eye = _point(landmarks, 263)
    nose = _point(landmarks, 1)
    eye_center_x = (left_eye[0] + right_eye[0]) / 2.0
    eye_center_y = (left_eye[1] + right_eye[1]) / 2.0
    face_width = abs(right_eye[0] - left_eye[0])
    if face_width < 0.02:
        return False

    horizontal_offset = abs(nose[0] - eye_center_x) / face_width
    vertical_offset = abs(nose[1] - eye_center_y) / face_width
    return horizontal_offset <= 0.18 and 0.18 <= vertical_offset <= 0.72


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
    face_detector = mp.solutions.face_mesh.FaceMesh(
        static_image_mode=False,
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=0.45,
        min_tracking_confidence=0.45,
    )

    sampled = 0
    pose_detected = 0
    face_detected = 0
    forward_gaze = 0
    stable_pose = 0
    smile_frames = 0
    tense_frames = 0
    natural_frames = 0
    shoulder_tilts: list[float] = []
    nose_positions: list[tuple[float, float]] = []
    movements: list[float] = []

    try:
        for frame_index in range(0, frame_count, step):
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, frame = cap.read()
            if not ok:
                continue

            sampled += 1
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            pose_result = pose_detector.process(rgb)
            face_result = face_detector.process(rgb)

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

            if face_result.multi_face_landmarks:
                face_detected += 1
                face_landmarks = face_result.multi_face_landmarks[0]
                if _is_forward_gaze(face_landmarks):
                    forward_gaze += 1

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
    finally:
        cap.release()
        pose_detector.close()
        face_detector.close()

    if sampled == 0 or pose_detected == 0 and face_detected == 0:
        return _fallback(duration_seconds, "no_detectable_frames")

    avg_tilt = mean(shoulder_tilts) if shoulder_tilts else FALLBACK_METRICS["shoulderTiltDegrees"]
    avg_movement = mean(movements) if movements else 0.0
    head_movement_ratio = _clamp(avg_movement / 0.08)
    posture_stability_ratio = stable_pose / pose_detected if pose_detected else FALLBACK_METRICS["postureStabilityRatio"]
    eye_contact_ratio = forward_gaze / face_detected if face_detected else FALLBACK_METRICS["eyeContactRatio"]
    tense_ratio = tense_frames / face_detected if face_detected else FALLBACK_METRICS["tenseExpressionRatio"]
    smile_ratio = smile_frames / face_detected if face_detected else FALLBACK_METRICS["smileRatio"]
    natural_ratio = natural_frames / face_detected if face_detected else FALLBACK_METRICS["naturalExpressionRatio"]

    happy_ratio = _clamp(smile_ratio)
    anxious_ratio = _clamp(tense_ratio)
    neutral_ratio = _clamp(1.0 - happy_ratio - anxious_ratio)

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
        "poseDetectionRatio": round(pose_detected / sampled, 3),
        "faceDetectionRatio": round(face_detected / sampled, 3),
        "visionAnalysisFallback": False,
    }
