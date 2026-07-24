from __future__ import annotations

import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from app.services.face_analyzer import FaceAnalyzer
from app.services.vision_metrics import (
    _create_face_detector,
    _enable_auto_orientation,
    _face_box_ratio,
    _face_landmark_box_ratio,
)


class VisionMetricsTest(unittest.TestCase):
    def test_face_detector_uses_static_mode_for_seeked_frames(self) -> None:
        constructor = Mock(return_value=object())
        mp = SimpleNamespace(
            solutions=SimpleNamespace(
                face_mesh=SimpleNamespace(FaceMesh=constructor),
            )
        )

        detector = _create_face_detector(mp)

        self.assertIsNotNone(detector)
        self.assertTrue(constructor.call_args.kwargs["static_image_mode"])

    def test_landmark_box_ratio_uses_normalized_face_area(self) -> None:
        landmarks = SimpleNamespace(
            landmark=[
                SimpleNamespace(x=0.2, y=0.3),
                SimpleNamespace(x=0.6, y=0.8),
            ]
        )

        self.assertAlmostEqual(_face_landmark_box_ratio(landmarks), 0.2)

    def test_crop_box_ratio_uses_full_frame_area(self) -> None:
        frame = SimpleNamespace(shape=(720, 1280, 3))

        ratio = _face_box_ratio(frame, (100, 120, 128, 144))

        self.assertAlmostEqual(ratio, 0.02)

    def test_face_analyzer_preserves_detection_diagnostics(self) -> None:
        analyzer = FaceAnalyzer()
        diagnostics = {
            "naturalExpressionRatio": 0.7,
            "tenseExpressionRatio": 0.2,
            "smileRatio": 0.1,
            "emotionRatio": {},
            "faceBoxRatioMin": 0.01,
            "faceBoxRatioAverage": 0.02,
            "faceBoxRatioMax": 0.03,
            "visionAttemptedFrameCount": 10,
            "visionReadFailureCount": 1,
            "videoRotationDegrees": 90.0,
            "videoOrientationAutoEnabled": True,
        }

        with unittest.mock.patch(
            "app.services.face_analyzer.analyze_vision_metrics",
            return_value=diagnostics,
        ):
            result = analyzer.analyze(10.0, "sample.mp4")

        self.assertEqual(result["faceBoxRatioAverage"], 0.02)
        self.assertEqual(result["visionReadFailureCount"], 1)
        self.assertEqual(result["videoRotationDegrees"], 90.0)
        self.assertTrue(result["videoOrientationAutoEnabled"])

    def test_auto_orientation_is_enabled_when_opencv_supports_it(self) -> None:
        cv2 = SimpleNamespace(CAP_PROP_ORIENTATION_AUTO=49)
        capture = Mock()
        capture.set.return_value = True

        enabled = _enable_auto_orientation(cv2, capture)

        self.assertTrue(enabled)
        capture.set.assert_called_once_with(49, 1)


if __name__ == "__main__":
    unittest.main()
