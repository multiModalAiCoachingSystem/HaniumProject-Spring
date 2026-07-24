from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


ENGINE_ROOT = Path(__file__).resolve().parents[1]
if str(ENGINE_ROOT) not in sys.path:
    sys.path.insert(0, str(ENGINE_ROOT))

from app.services.basic_analyzer import BasicAnalyzer
from app.services.vision_metrics import analyze_vision_metrics


METRIC_KEYS = (
    "visionFrameCount",
    "visionAttemptedFrameCount",
    "visionReadFailureCount",
    "poseDetectionRatio",
    "faceAttemptedFrameCount",
    "faceDetectedFrameCount",
    "faceFullFrameDetectedCount",
    "faceCropRetryAttemptedCount",
    "faceCropRetryDetectedCount",
    "faceDetectionRatio",
    "faceBoxRatioMin",
    "faceBoxRatioAverage",
    "faceBoxRatioMax",
    "gazeAttemptedFrameCount",
    "gazeDetectedFrameCount",
    "gazeDetectionRatio",
    "eyeContactRatio",
    "naturalExpressionRatio",
    "tenseExpressionRatio",
    "smileRatio",
    "gestureSampledFrameCount",
    "gestureValidFrameCount",
    "gestureVisibleRatio",
    "gestureAverageMovement",
    "gestureMovementRatio",
    "gestureCountPerMinute",
    "gestureActivityScore",
    "visionAnalysisFallback",
    "visionAnalysisFallbackReason",
    "gestureAnalysisFallback",
    "gestureAnalysisFallbackReason",
    "videoRotationDegrees",
    "videoOrientationAutoEnabled",
)


def _sample(value: str) -> tuple[str, Path]:
    label, separator, raw_path = value.partition("=")
    if not separator or not label.strip() or not raw_path.strip():
        raise argparse.ArgumentTypeError("sample must use LABEL=PATH")
    path = Path(raw_path).expanduser().resolve()
    if not path.is_file():
        raise argparse.ArgumentTypeError(f"sample file does not exist: {label}")
    return label.strip(), path


def _video_metadata(path: Path) -> dict[str, Any]:
    import cv2

    capture = cv2.VideoCapture(str(path))
    opened = capture.isOpened()
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    readable, _ = capture.read() if opened else (False, None)
    capture.release()
    duration = frame_count / fps if fps > 0 else 0.0
    rotation = 0.0
    try:
        probe = subprocess.run(
            [
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream_side_data=rotation",
                "-of",
                "json",
                str(path),
            ],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=30,
        )
        side_data = json.loads(probe.stdout).get("streams", [{}])[0].get("side_data_list", [])
        rotation = float(next((item["rotation"] for item in side_data if "rotation" in item), 0.0))
    except (KeyError, IndexError, OSError, ValueError, subprocess.SubprocessError, json.JSONDecodeError):
        rotation = 0.0

    return {
        "extension": path.suffix.lower(),
        "sizeBytes": path.stat().st_size,
        "width": width,
        "height": height,
        "fps": round(fps, 3),
        "frameCount": frame_count,
        "durationSeconds": round(duration, 3),
        "rotationDegrees": rotation,
        "opencvOpened": opened,
        "firstFrameReadable": bool(readable),
    }


def _score_map(result: dict[str, Any]) -> dict[str, float]:
    return {
        str(item.get("category")): float(item.get("score", 0.0))
        for item in result.get("scores", [])
    }


def _measure(label: str, path: Path, repeat_index: int) -> dict[str, Any]:
    analyze_vision_metrics.cache_clear()
    result = BasicAnalyzer().analyze(
        job_id=-(repeat_index + 1),
        video_id=-(repeat_index + 1),
        file_path=str(path),
        transcript="실제 영상 회귀 분석용 대본",
    )
    metrics = result["metrics"]
    scores = _score_map(result)
    return {
        "sample": label,
        "repeat": repeat_index + 1,
        "metrics": {key: metrics.get(key) for key in METRIC_KEYS},
        "scores": {
            "posture": scores.get("POSTURE"),
            "expression": scores.get("EXPRESSION"),
            "eyeContact": scores.get("EYE_CONTACT"),
            "gesture": scores.get("GESTURE"),
            "voice": scores.get("VOICE"),
            "total": result.get("totalScore"),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run repeatable real-video vision regression measurements.")
    parser.add_argument("--sample", action="append", required=True, type=_sample, metavar="LABEL=PATH")
    parser.add_argument("--repeat", type=int, default=2)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.repeat < 1:
        parser.error("--repeat must be at least 1")

    payload = {
        "metadata": {label: _video_metadata(path) for label, path in args.sample},
        "runs": [
            _measure(label, path, repeat_index)
            for repeat_index in range(args.repeat)
            for label, path in args.sample
        ],
    }
    encoded = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    else:
        print(encoded)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
