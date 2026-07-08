import unittest

from app.services.gesture_analyzer import GestureAnalyzer


class GestureAnalyzerTest(unittest.TestCase):
    def test_missing_video_uses_safe_fallback(self):
        metrics = GestureAnalyzer().analyze(60.0, None)

        self.assertTrue(metrics["gestureAnalysisFallback"])
        self.assertEqual(metrics["gestureAnalysisFallbackReason"], "video_file_missing")
        self.assertGreaterEqual(metrics["gestureActivityScore"], 0.0)
        self.assertLessEqual(metrics["gestureActivityScore"], 100.0)
        self.assertEqual(metrics["gestureVisibleRatio"], 0.0)
        self.assertEqual(metrics["gestureAverageMovement"], 0.0)

    def test_moderate_movement_produces_bounded_metrics(self):
        analyzer = GestureAnalyzer()

        metrics = analyzer._metrics_from_movements(
            duration_seconds=60.0,
            sampled=30,
            valid_frames=28,
            visible_ratio=0.933,
            movements=[0.07, 0.08, 0.06, 0.09, 0.05, 0.07],
            raw_movements=[0.07, 0.08, 0.06, 0.09, 0.05, 0.07],
            left_movements=[0.07, 0.08, 0.06, 0.09, 0.05, 0.07],
            right_movements=[0.06, 0.07, 0.06, 0.08, 0.05, 0.07],
            active_frames=6,
            outlier_frames=0,
            still_frames=0,
        )

        self.assertFalse(metrics["gestureAnalysisFallback"])
        self.assertGreaterEqual(metrics["gestureActivityScore"], 0.0)
        self.assertLessEqual(metrics["gestureActivityScore"], 100.0)
        self.assertGreater(metrics["gestureVisibleRatio"], 0.9)
        self.assertGreater(metrics["gestureAverageMovement"], 0.0)
        self.assertGreater(metrics["gestureBalance"], 0.8)
        self.assertEqual(metrics["gestureStability"], 1.0)


if __name__ == "__main__":
    unittest.main()
