function finiteRatio(value, fallback = 1) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(0, Math.min(1, number));
}

function derivedLabel(ratio, fallbackUsed) {
  if (fallbackUsed || ratio <= 0) return '분석 불가';
  if (ratio < 0.1) return '감지 신뢰도 매우 낮음';
  if (ratio < 0.3) return '감지 신뢰도 낮음';
  if (ratio < 0.5) return '감지 신뢰도 제한됨';
  return '감지 신뢰도 정상';
}

export function visualReliability(metrics = {}, category) {
  const expression = category === 'EXPRESSION';
  const ratio = expression
    ? finiteRatio(metrics.faceDetectionRatio)
    : Math.min(
      finiteRatio(metrics.faceDetectionRatio),
      finiteRatio(metrics.gazeDetectionRatio, finiteRatio(metrics.faceDetectionRatio)),
    );
  const fallbackUsed = expression
    ? Boolean(metrics.expressionFallbackUsed ?? metrics.expressionFallback ?? metrics.visionAnalysisFallback)
    : Boolean(metrics.gazeFallbackUsed ?? metrics.visionAnalysisFallback);
  const explicitLabel = expression
    ? metrics.expressionReliabilityLabel
    : metrics.gazeReliabilityLabel;
  const lowConfidence = expression
    ? Boolean(metrics.expressionLowConfidence) || ratio < 0.3
    : Boolean(metrics.gazeLowConfidence) || ratio < 0.3;

  return {
    ratio,
    label: explicitLabel || derivedLabel(ratio, fallbackUsed),
    lowConfidence,
    limited: fallbackUsed || ratio < 0.5,
  };
}

