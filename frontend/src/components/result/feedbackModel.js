const CATEGORY_LABELS = {
  POSTURE: '자세',
  EYE_CONTACT: '시선 처리',
  VOICE: '음성',
  GESTURE: '제스처',
  EXPRESSION: '표정',
  VIDEO_QUALITY: '화면 가시성',
  GENERAL: '종합',
};

const LEVEL_LABELS = {
  STRENGTH: '강점',
  IMPROVEMENT: '개선 필요',
  NEUTRAL: '참고',
};

function canonicalLevel(item = {}) {
  const value = String(item.level || '').trim().toUpperCase();
  return Object.hasOwn(LEVEL_LABELS, value) ? value : null;
}

function canonicalCategory(item = {}) {
  const value = String(item.category || 'GENERAL').trim().toUpperCase();
  return Object.hasOwn(CATEGORY_LABELS, value) ? value : 'GENERAL';
}

function timestamp(item = {}) {
  const value = Number(item.timestampSeconds ?? item.startSeconds ?? 0);
  return Number.isFinite(value) ? Math.max(0, value) : 0;
}

function hasKorean(text) {
  return /[ㄱ-ㅎㅏ-ㅣ가-힣]/.test(String(text || ''));
}

function normalizeText(text) {
  return String(text || '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
}

function containsAny(text, values) {
  return values.some((value) => text.includes(value));
}

function conflictsWithLevel(text, level) {
  const compact = normalizeText(text);
  if (level === 'STRENGTH') {
    return containsAny(compact, ['개선', '보완', '부족', '줄여', '필요', '벗어나', '어렵', '불안정']);
  }
  if (level === 'IMPROVEMENT') {
    return containsAny(compact, ['강점', '우수', '현재와같', '안정적으로관찰', '잘유지']);
  }
  return false;
}

function deterministicTitle(category, level) {
  return `${CATEGORY_LABELS[category]} ${LEVEL_LABELS[level]}`;
}

function fallbackDescription(category, level) {
  const descriptions = {
    STRENGTH: {
      POSTURE: '발표 중 안정적인 자세가 관찰되었습니다.',
      EYE_CONTACT: '청중 또는 카메라를 향한 시선 흐름이 비교적 안정적으로 관찰되었습니다.',
      VOICE: '발표 내용을 전달하는 발화 흐름이 안정적으로 관찰되었습니다.',
      GESTURE: '발표 내용을 보조하는 손동작이나 몸의 움직임이 관찰되었습니다.',
      EXPRESSION: '발표 분위기에 어울리는 자연스러운 표정 흐름이 관찰되었습니다.',
      VIDEO_QUALITY: '발표자의 모습이 분석에 활용될 수 있을 정도로 확인되었습니다.',
      GENERAL: '발표 영상에서 긍정적인 전달 요소가 관찰되었습니다.',
    },
    IMPROVEMENT: {
      POSTURE: '자세나 상체 움직임에서 보완하면 좋을 부분이 관찰되었습니다.',
      EYE_CONTACT: '시선이 카메라나 청중 방향에서 벗어나는 구간이 관찰되었습니다.',
      VOICE: '발화 속도나 음량에서 보완하면 좋을 부분이 관찰되었습니다.',
      GESTURE: '제스처의 양이나 안정성에서 보완하면 좋을 부분이 관찰되었습니다.',
      EXPRESSION: '표정 변화나 긴장감에서 보완하면 좋을 부분이 관찰되었습니다.',
      VIDEO_QUALITY: '얼굴이나 상반신이 충분히 보이지 않는 구간이 관찰되었습니다.',
      GENERAL: '발표 영상에서 보완하면 좋을 전달 요소가 관찰되었습니다.',
    },
    NEUTRAL: {
      VIDEO_QUALITY: '촬영 품질로 인해 일부 분석 결과의 신뢰도가 낮을 수 있습니다.',
      GENERAL: '분석 품질을 확인할 필요가 있습니다.',
    },
  };
  return descriptions[level]?.[category] || descriptions[level]?.GENERAL || '';
}

function deterministicRecommendation(category, level) {
  const recommendations = {
    STRENGTH: {
      POSTURE: '앞으로의 발표에서도 현재와 같은 안정적인 자세를 유지해 보세요.',
      EYE_CONTACT: '핵심 문장을 말할 때 현재와 같은 시선 흐름을 유지해 보세요.',
      VOICE: '앞으로의 발표에서도 현재와 같은 안정적인 발화 흐름을 유지해 보세요.',
      GESTURE: '강조할 문장에서 현재와 같은 자연스러운 제스처를 유지해 보세요.',
      EXPRESSION: '중요한 내용 앞뒤에서도 현재처럼 자연스러운 표정을 유지해 보세요.',
      VIDEO_QUALITY: '비슷한 촬영 구도와 밝기를 유지해 보세요.',
      GENERAL: '현재의 긍정적인 전달 흐름을 다음 발표에서도 유지해 보세요.',
    },
    IMPROVEMENT: {
      POSTURE: '어깨와 상체를 곧게 유지하고 불필요한 흔들림을 줄여 보세요.',
      EYE_CONTACT: '문장 단위로 카메라나 청중을 다시 바라보는 연습을 해 보세요.',
      VOICE: '핵심 문장에서 속도와 음량을 일정하게 유지하는 연습을 해 보세요.',
      GESTURE: '강조할 문장에 맞춰 손동작을 짧고 명확하게 사용해 보세요.',
      EXPRESSION: '핵심 문장을 말하기 전후로 표정을 풀고 자연스럽게 말해 보세요.',
      VIDEO_QUALITY: '얼굴과 상반신이 화면 안에 안정적으로 들어오도록 촬영 구도를 조정해 보세요.',
      GENERAL: '다음 리허설에서 해당 구간의 전달 방식을 다시 점검해 보세요.',
    },
    NEUTRAL: {
      VIDEO_QUALITY: '밝기와 촬영 구도를 조정한 뒤 다시 분석해 보세요.',
      GENERAL: '분석 품질 안내를 참고해 촬영 환경을 점검해 보세요.',
    },
  };
  return recommendations[level]?.[category] || recommendations[level]?.GENERAL || '';
}

function displayDescription(item, category, level) {
  for (const value of [item.descriptionKo, item.description]) {
    if (hasKorean(value) && !conflictsWithLevel(value, level)) return value;
  }
  return fallbackDescription(category, level);
}

function displayItem(item) {
  const category = canonicalCategory(item);
  const level = canonicalLevel(item);
  return {
    ...item,
    category,
    level,
    startSeconds: timestamp(item),
    title: deterministicTitle(category, level),
    description: displayDescription(item, category, level),
    recommendation: deterministicRecommendation(category, level),
  };
}

function isDuplicate(left, right) {
  if (left.category !== right.category || left.level !== right.level) return false;
  if (Math.abs(left.startSeconds - right.startSeconds) > 2) return false;
  return normalizeText(left.description) === normalizeText(right.description)
    || Math.round(left.startSeconds) === Math.round(right.startSeconds);
}

function preferred(left, right) {
  const leftConfidence = Number(left.confidence) || 0;
  const rightConfidence = Number(right.confidence) || 0;
  if (leftConfidence !== rightConfidence) return leftConfidence > rightConfidence ? left : right;
  return String(left.description || '').length >= String(right.description || '').length ? left : right;
}

export function prepareFeedback(feedback = []) {
  const unique = [];
  for (const source of Array.isArray(feedback) ? feedback : []) {
    const level = canonicalLevel(source);
    if (!level || (level === 'NEUTRAL' && source.displayNeutral !== true)) continue;
    const item = displayItem(source);
    const duplicateIndex = unique.findIndex((existing) => isDuplicate(existing, item));
    if (duplicateIndex < 0) unique.push(item);
    else unique[duplicateIndex] = preferred(unique[duplicateIndex], item);
  }
  unique.sort((left, right) => left.startSeconds - right.startSeconds);
  return unique;
}

export function feedbackKey(item) {
  return [
    item.category,
    item.level,
    Math.round(item.startSeconds),
    normalizeText(item.description),
  ].join('|');
}
