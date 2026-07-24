import unittest

from app.services.score_calculator import ScoreCalculator
from app.services.basic_analyzer import BasicAnalyzer


class ScoreCalculatorExpressionTest(unittest.TestCase):
    def setUp(self):
        self.calculator = ScoreCalculator()

    def expression_score(self, **metrics):
        return self.calculator.expression_score(metrics).score

    def expression_item(self, **metrics):
        return self.calculator.expression_score(metrics)

    def test_perfect_natural_expression_does_not_always_saturate(self):
        score = self.expression_score(
            naturalExpressionRatio=1.0,
            tenseExpressionRatio=0.0,
            smileRatio=0.0,
            faceDetectionRatio=0.75,
            visionAnalysisFallback=False,
        )

        self.assertEqual(score, 87.0)

    def test_smile_bonus_is_limited(self):
        moderate_smile = self.expression_score(
            naturalExpressionRatio=1.0,
            tenseExpressionRatio=0.0,
            smileRatio=0.4,
            faceDetectionRatio=0.75,
            visionAnalysisFallback=False,
        )
        high_smile = self.expression_score(
            naturalExpressionRatio=1.0,
            tenseExpressionRatio=0.0,
            smileRatio=1.0,
            faceDetectionRatio=0.75,
            visionAnalysisFallback=False,
        )

        self.assertEqual(moderate_smile, 91.5)
        self.assertEqual(high_smile, 88.0)

    def test_tense_expression_penalty_lowers_score(self):
        score = self.expression_score(
            naturalExpressionRatio=0.82,
            tenseExpressionRatio=0.35,
            smileRatio=0.1,
            faceDetectionRatio=0.75,
            visionAnalysisFallback=False,
        )

        self.assertLess(score, 50.0)

    def test_low_face_detection_caps_expression_score(self):
        score = self.expression_score(
            naturalExpressionRatio=1.0,
            tenseExpressionRatio=0.0,
            smileRatio=0.6,
            faceDetectionRatio=0.087,
            faceDetectedFrameCount=21,
            faceAttemptedFrameCount=240,
            visionAnalysisFallback=False,
        )

        self.assertEqual(score, 60.0)

    def test_very_low_face_detection_is_not_scored_as_excellent(self):
        item = self.expression_item(
            naturalExpressionRatio=1.0,
            tenseExpressionRatio=0.0,
            smileRatio=0.0,
            faceDetectionRatio=0.013,
            faceDetectedFrameCount=3,
            faceAttemptedFrameCount=240,
            visionAnalysisFallback=False,
        )

        self.assertEqual(item.score, 60.0)
        self.assertEqual(item.metrics["expressionConfidenceCap"], 60.0)
        self.assertEqual(item.metrics["expressionConfidenceReason"], "very_low_face_detection")
        self.assertFalse(item.metrics["expressionAnalysisReliable"])

    def test_enough_ratio_but_too_few_detected_frames_is_low_confidence(self):
        item = self.expression_item(
            naturalExpressionRatio=1.0,
            tenseExpressionRatio=0.0,
            smileRatio=0.4,
            faceDetectionRatio=0.20,
            faceDetectedFrameCount=4,
            faceAttemptedFrameCount=20,
            visionAnalysisFallback=False,
        )

        self.assertEqual(item.score, 60.0)
        self.assertEqual(item.metrics["expressionConfidenceReason"], "very_low_face_detection")
        self.assertTrue(item.metrics["expressionLowConfidence"])

    def test_ratio_values_are_clamped_to_unit_range(self):
        score = self.expression_score(
            naturalExpressionRatio=100.0,
            tenseExpressionRatio=0.0,
            smileRatio=100.0,
            faceDetectionRatio=0.75,
            faceDetectedFrameCount=100,
            visionAnalysisFallback=False,
        )

        self.assertEqual(score, 88.0)

    def test_fallback_expression_is_conservative(self):
        score = self.expression_score(
            naturalExpressionRatio=0.74,
            tenseExpressionRatio=0.12,
            smileRatio=0.18,
            faceDetectionRatio=0.0,
            faceDetectedFrameCount=0,
            visionAnalysisFallback=True,
        )

        self.assertLessEqual(score, 50.0)

    def test_high_detection_keeps_raw_expression_and_gaze_scores(self):
        metrics = {
            "naturalExpressionRatio": 1.0,
            "tenseExpressionRatio": 0.0,
            "smileRatio": 0.4,
            "faceDetectionRatio": 0.70,
            "faceDetectedFrameCount": 168,
            "gazeDetectionRatio": 0.70,
            "gazeDetectedFrameCount": 168,
            "eyeContactRatio": 0.8,
            "visionAnalysisFallback": False,
        }

        expression = self.calculator.expression_score(metrics)
        gaze = self.calculator.eye_contact_score(metrics)

        self.assertEqual(expression.score, 91.5)
        self.assertEqual(gaze.score, 94.5)
        self.assertTrue(expression.metrics["expressionAnalysisReliable"])
        self.assertTrue(gaze.metrics["gazeAnalysisReliable"])

    def test_thirty_percent_detection_limits_scores_to_85(self):
        metrics = {
            "naturalExpressionRatio": 1.0,
            "tenseExpressionRatio": 0.0,
            "smileRatio": 0.4,
            "faceDetectionRatio": 0.30,
            "faceDetectedFrameCount": 72,
            "gazeDetectionRatio": 0.30,
            "gazeDetectedFrameCount": 72,
            "eyeContactRatio": 0.8,
            "visionAnalysisFallback": False,
        }

        self.assertEqual(self.calculator.expression_score(metrics).score, 85.0)
        self.assertEqual(self.calculator.eye_contact_score(metrics).score, 85.0)

    def test_very_low_detection_prevents_high_expression_and_gaze_scores(self):
        metrics = {
            "naturalExpressionRatio": 1.0,
            "tenseExpressionRatio": 0.0,
            "smileRatio": 0.4,
            "faceDetectionRatio": 0.087,
            "faceDetectedFrameCount": 21,
            "faceAttemptedFrameCount": 240,
            "gazeDetectionRatio": 0.087,
            "gazeDetectedFrameCount": 21,
            "gazeAttemptedFrameCount": 240,
            "eyeContactRatio": 0.762,
            "visionAnalysisFallback": False,
        }

        expression = self.calculator.expression_score(metrics)
        gaze = self.calculator.eye_contact_score(metrics)

        self.assertEqual(expression.score, 60.0)
        self.assertEqual(gaze.score, 60.0)
        self.assertEqual(gaze.metrics["eyeContactRawScore"], 93.5)
        self.assertTrue(expression.metrics["expressionLowConfidence"])
        self.assertTrue(gaze.metrics["gazeLowConfidence"])

    def test_no_face_detection_uses_conservative_visual_caps(self):
        metrics = {
            "faceDetectionRatio": 0.0,
            "faceDetectedFrameCount": 0,
            "gazeDetectionRatio": 0.0,
            "gazeDetectedFrameCount": 0,
            "eyeContactRatio": 0.68,
            "visionAnalysisFallback": True,
        }

        expression = self.calculator.expression_score(metrics)
        gaze = self.calculator.eye_contact_score(metrics)

        self.assertLessEqual(expression.score, 50.0)
        self.assertLessEqual(gaze.score, 50.0)
        self.assertTrue(expression.metrics["expressionFallbackUsed"])
        self.assertTrue(gaze.metrics["gazeFallbackUsed"])

    def test_visual_reliability_metadata_is_merged_into_response_metrics(self):
        metrics = {
            "faceDetectionRatio": 0.087,
            "faceDetectedFrameCount": 21,
            "gazeDetectionRatio": 0.087,
            "gazeDetectedFrameCount": 21,
            "eyeContactRatio": 0.762,
            "visionAnalysisFallback": False,
        }
        result = self.calculator.calculate(metrics)

        BasicAnalyzer._merge_visual_score_metrics(metrics, result)

        self.assertEqual(metrics["expressionScore"], 60.0)
        self.assertEqual(metrics["eyeContactScore"], 60.0)
        self.assertEqual(metrics["expressionDetectionConfidence"], 0.25)
        self.assertEqual(metrics["gazeDetectionConfidence"], 0.25)

    def test_strength_observation_does_not_include_improvement_copy(self):
        observations = self.calculator.observations(
            [
                {
                    "category": "EYE_CONTACT",
                    "name": "시선 처리",
                    "score": 90.0,
                    "metrics": {"eyeContactRatio": 0.1},
                }
            ],
            duration_seconds=30.0,
        )

        self.assertEqual(observations[0]["type"], "STRENGTH")
        self.assertNotIn("부족", observations[0]["description"])
        self.assertIn("유지", observations[0]["suggestion"])


if __name__ == "__main__":
    unittest.main()
