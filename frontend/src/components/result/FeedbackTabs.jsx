function formatTime(seconds = 0) {
  const safeSeconds = Number.isFinite(Number(seconds)) ? Number(seconds) : 0;
  const minute = Math.floor(safeSeconds / 60).toString().padStart(2, '0');
  const second = Math.floor(safeSeconds % 60).toString().padStart(2, '0');
  return `${minute}:${second}`;
}

const CATEGORY_LABELS = {
  POSTURE: '자세',
  EYE_CONTACT: '시선 처리',
  GAZE: '시선 처리',
  GESTURE: '제스처',
  EXPRESSION: '표정',
  VIDEO_QUALITY: '화면 가시성',
  VISIBILITY: '화면 가시성',
  GENERAL: '종합',
};

const LEVEL_LABELS = {
  STRENGTH: '강점',
  IMPROVEMENT: '개선 필요',
  NEUTRAL: '참고',
};

function normalizeLevel(item = {}) {
  const raw = String(item.level || item.type || '').trim().toUpperCase();
  if (raw === 'STRENGTH') return 'STRENGTH';
  if (raw === 'IMPROVEMENT' || raw === 'NEEDS_IMPROVEMENT' || raw === 'WEAKNESS') {
    return 'IMPROVEMENT';
  }
  return 'NEUTRAL';
}

function normalizeCategory(item = {}) {
  const raw = String(item.category || 'GENERAL').trim().toUpperCase();
  if (raw === 'GAZE') return 'EYE_CONTACT';
  if (raw === 'VISIBILITY') return 'VIDEO_QUALITY';
  return CATEGORY_LABELS[raw] ? raw : 'GENERAL';
}

function hasKorean(text) {
  return /[ㄱ-ㅎㅏ-ㅣ가-힣]/.test(String(text || ''));
}

function categoryLabel(category) {
  return CATEGORY_LABELS[category] || '종합';
}

function levelLabel(level) {
  return LEVEL_LABELS[level] || '참고';
}

function deterministicTitle(item) {
  return `${categoryLabel(normalizeCategory(item))} ${levelLabel(normalizeLevel(item))}`;
}

function fallbackDescription(item) {
  const category = normalizeCategory(item);
  const level = normalizeLevel(item);
  if (level === 'STRENGTH') {
    if (category === 'POSTURE') return '발표 중 안정적인 자세가 관찰되었습니다.';
    if (category === 'EYE_CONTACT') return '청중 또는 카메라를 향한 시선 흐름이 비교적 안정적으로 관찰되었습니다.';
    if (category === 'GESTURE') return '발표 내용을 보조하는 손동작이나 몸의 움직임이 관찰되었습니다.';
    if (category === 'EXPRESSION') return '발표 분위기에 어울리는 자연스러운 표정 흐름이 관찰되었습니다.';
    if (category === 'VIDEO_QUALITY') return '발표자의 모습이 분석에 활용될 수 있을 정도로 확인되었습니다.';
    return '발표 영상에서 긍정적인 시각적 요소가 관찰되었습니다.';
  }
  if (level === 'IMPROVEMENT') {
    if (category === 'POSTURE') return '자세나 상체 움직임에서 보완하면 좋을 부분이 관찰되었습니다.';
    if (category === 'EYE_CONTACT') return '시선이 카메라나 청중 방향에서 벗어나는 구간이 관찰되었습니다.';
    if (category === 'GESTURE') return '제스처의 양이나 안정성에서 보완하면 좋을 부분이 관찰되었습니다.';
    if (category === 'EXPRESSION') return '표정 변화나 긴장감에서 보완하면 좋을 부분이 관찰되었습니다.';
    if (category === 'VIDEO_QUALITY') return '얼굴이나 상반신이 충분히 보이지 않는 구간이 관찰되었습니다.';
    return '발표 영상에서 보완하면 좋을 시각적 요소가 관찰되었습니다.';
  }
  if (category === 'POSTURE') return '자세와 상체 움직임을 참고할 수 있는 구간입니다.';
  if (category === 'EYE_CONTACT') return '시선 흐름을 참고할 수 있는 구간입니다.';
  if (category === 'GESTURE') return '제스처 사용을 참고할 수 있는 구간입니다.';
  if (category === 'EXPRESSION') return '표정 흐름을 참고할 수 있는 구간입니다.';
  if (category === 'VIDEO_QUALITY') return '영상 가시성을 참고할 수 있는 구간입니다.';
  return '발표 영상에서 참고할 수 있는 시각적 관찰입니다.';
}

function fallbackRecommendation(item) {
  const category = normalizeCategory(item);
  const level = normalizeLevel(item);
  if (level === 'STRENGTH') {
    if (category === 'POSTURE') return '앞으로의 발표에서도 현재와 같은 안정적인 자세를 유지해 보세요.';
    if (category === 'EYE_CONTACT') return '핵심 문장을 말할 때 현재와 같은 시선 흐름을 유지해 보세요.';
    if (category === 'GESTURE') return '강조할 문장에서 현재와 같은 자연스러운 제스처를 유지해 보세요.';
    if (category === 'EXPRESSION') return '중요한 내용 앞뒤에서도 현재처럼 자연스러운 표정을 유지해 보세요.';
    if (category === 'VIDEO_QUALITY') return '비슷한 촬영 구도와 밝기를 유지하면 분석 품질도 안정적입니다.';
    return '현재의 긍정적인 시각적 흐름을 다음 발표에서도 유지해 보세요.';
  }
  if (level === 'IMPROVEMENT') {
    if (category === 'POSTURE') return '어깨와 상체를 곧게 유지하고 불필요한 흔들림을 줄여 보세요.';
    if (category === 'EYE_CONTACT') return '문장 단위로 카메라나 청중을 다시 바라보는 연습을 해 보세요.';
    if (category === 'GESTURE') return '강조할 문장에 맞춰 손동작을 짧고 명확하게 사용해 보세요.';
    if (category === 'EXPRESSION') return '핵심 문장을 말하기 전후로 표정을 풀고 자연스럽게 말해 보세요.';
    if (category === 'VIDEO_QUALITY') return '얼굴과 상반신이 화면 안에 안정적으로 들어오도록 촬영 구도를 조정해 보세요.';
    return '다음 리허설에서 이 구간의 시각적 전달 방식을 한 번 더 점검해 보세요.';
  }
  return '이 구간을 참고해 다음 리허설에서 발표 자세와 시각적 전달 방식을 점검해 보세요.';
}

function displayText(item, koKey, rawKey, fallback) {
  if (item[koKey] && hasKorean(item[koKey])) return item[koKey];
  if (hasKorean(item[rawKey])) return item[rawKey];
  return fallback;
}

function displayItem(item) {
  return {
    title: deterministicTitle(item),
    description: displayText(item, 'descriptionKo', 'description', fallbackDescription(item)),
    recommendation: displayText(
      item,
      'recommendationKo',
      'suggestion',
      fallbackRecommendation(item),
    ),
  };
}

export default function FeedbackTabs({ feedback = [], tab, onTabChange, onSeek }) {
  const filtered = feedback
    .filter((item) => normalizeLevel(item) === tab)
    .sort((a, b) => (a.startSeconds || 0) - (b.startSeconds || 0));

  return (
    <section className="panel">
      <div className="tabs">
        <button className={tab === 'STRENGTH' ? 'active' : ''} onClick={() => onTabChange('STRENGTH')}>
          강점
        </button>
        <button className={tab === 'IMPROVEMENT' ? 'active' : ''} onClick={() => onTabChange('IMPROVEMENT')}>
          개선 필요
        </button>
        <button className={tab === 'NEUTRAL' ? 'active' : ''} onClick={() => onTabChange('NEUTRAL')}>
          참고
        </button>
      </div>
      {filtered.map((item) => {
        const display = displayItem(item);
        return (
          <article className="timeline" key={item.id}>
            <button onClick={() => onSeek(item.startSeconds)}>{formatTime(item.startSeconds)}</button>
            <div>
              <h2>{display.title}</h2>
              <p>{display.description}</p>
              {display.recommendation && <p>{display.recommendation}</p>}
            </div>
          </article>
        );
      })}
    </section>
  );
}
