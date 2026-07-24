import test from 'node:test';
import assert from 'node:assert/strict';
import { visualReliability } from '../src/utils/visualReliability.js';

test('낮은 얼굴 감지율은 표정 신뢰도를 매우 낮음으로 표시한다', () => {
  const result = visualReliability({ faceDetectionRatio: 0.087 }, 'EXPRESSION');

  assert.equal(result.label, '감지 신뢰도 매우 낮음');
  assert.equal(result.lowConfidence, true);
});

test('시선 신뢰도는 얼굴과 시선 감지율 중 낮은 값을 따른다', () => {
  const result = visualReliability({ faceDetectionRatio: 0.8, gazeDetectionRatio: 0.2 }, 'EYE_CONTACT');

  assert.equal(result.ratio, 0.2);
  assert.equal(result.label, '감지 신뢰도 낮음');
});

test('충분한 감지율은 정상 신뢰도로 표시한다', () => {
  const result = visualReliability({ faceDetectionRatio: 0.7, gazeDetectionRatio: 0.7 }, 'EYE_CONTACT');

  assert.equal(result.label, '감지 신뢰도 정상');
  assert.equal(result.limited, false);
});
