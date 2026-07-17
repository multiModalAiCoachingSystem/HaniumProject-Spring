import unittest

from app.services.score_calculator import ScoreCalculator


class ScoreCalculatorExpressionTest(unittest.TestCase):
    def setUp(self):
        self.calculator = ScoreCalculator()

    def expression_score(self, **metrics):
        return self.calculator.expression_score(metrics).score

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
            visionAnalysisFallback=False,
        )

        self.assertEqual(score, 88.0)

    def test_fallback_expression_is_conservative(self):
        score = self.expression_score(
            naturalExpressionRatio=0.74,
            tenseExpressionRatio=0.12,
            smileRatio=0.18,
            faceDetectionRatio=0.0,
            visionAnalysisFallback=True,
        )

        self.assertLessEqual(score, 66.0)


if __name__ == "__main__":
    unittest.main()
