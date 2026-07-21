import test from 'node:test';
import assert from 'node:assert/strict';
import { formatSeoulDateTime } from '../src/utils/dateTime.js';

test('KST offset API 값을 한국 오후 시각으로 표시한다', () => {
  const result = formatSeoulDateTime('2026-07-21T16:28:00+09:00');

  assert.match(result, /2026/);
  assert.match(result, /07/);
  assert.match(result, /21/);
  assert.match(result, /오후/);
  assert.match(result, /04:28/);
});

test('UTC API 값도 Asia Seoul 기준으로 같은 시각을 표시한다', () => {
  assert.equal(
    formatSeoulDateTime('2026-07-21T07:28:00Z'),
    formatSeoulDateTime('2026-07-21T16:28:00+09:00'),
  );
});

test('자정을 넘는 시각과 빈 값을 안전하게 처리한다', () => {
  const result = formatSeoulDateTime('2026-07-21T15:05:00Z');

  assert.match(result, /2026/);
  assert.match(result, /07/);
  assert.match(result, /22/);
  assert.match(result, /오전/);
  assert.match(result, /12:05/);
  assert.equal(formatSeoulDateTime(null), '-');
  assert.equal(formatSeoulDateTime('invalid'), '-');
});
