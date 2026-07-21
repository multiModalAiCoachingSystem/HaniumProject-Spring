import test from 'node:test';
import assert from 'node:assert/strict';
import { feedbackKey, prepareFeedback } from '../src/components/result/feedbackModel.js';

test('같은 자세 강점 observation은 하나로 합친다', () => {
  const feedback = Array.from({ length: 5 }, (_, index) => ({
    category: 'POSTURE',
    level: 'STRENGTH',
    startSeconds: 8 + index * 0.1,
    descriptionKo: '전체적으로 안정적인 자세가 관찰되었습니다.',
    confidence: 0.7 + index * 0.01,
  }));

  const result = prepareFeedback(feedback);

  assert.equal(result.length, 1);
  assert.equal(result[0].confidence, 0.74);
  assert.equal(result[0].title, '자세 강점');
});

test('강점의 개선형 문구는 유지형 문구로 교정한다', () => {
  const [item] = prepareFeedback([{
    category: 'POSTURE',
    level: 'STRENGTH',
    startSeconds: 8,
    descriptionKo: '상체 흔들림을 줄일 필요가 있습니다.',
    recommendationKo: '어깨를 고정해 보세요.',
  }]);

  assert.equal(item.description, '발표 중 안정적인 자세가 관찰되었습니다.');
  assert.match(item.recommendation, /유지/);
});

test('개선 항목의 제목과 권장 문구는 level을 따른다', () => {
  const [item] = prepareFeedback([{
    category: 'GESTURE',
    level: 'IMPROVEMENT',
    startSeconds: 20,
    titleKo: '제스처 강점',
  }]);

  assert.equal(item.title, '제스처 개선 필요');
  assert.match(item.recommendation, /사용해 보세요/);
});

test('일반 NEUTRAL과 알 수 없는 level은 숨긴다', () => {
  const result = prepareFeedback([
    { category: 'POSTURE', level: 'NEUTRAL', startSeconds: 1 },
    { category: 'GAZE', level: 'UNKNOWN', startSeconds: 2 },
  ]);

  assert.deepEqual(result, []);
});

test('분석 품질 NEUTRAL만 참고 항목으로 유지한다', () => {
  const [item] = prepareFeedback([{
    category: 'VIDEO_QUALITY',
    level: 'NEUTRAL',
    displayNeutral: true,
    startSeconds: 3,
  }]);

  assert.equal(item.level, 'NEUTRAL');
  assert.equal(item.title, '화면 가시성 참고');
  assert.ok(feedbackKey(item).startsWith('VIDEO_QUALITY|NEUTRAL|3|'));
});
