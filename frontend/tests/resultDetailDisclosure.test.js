import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../src/pages/ResultDetailPage.jsx', import.meta.url), 'utf8');

test('상세 분석 지표는 기본적으로 닫힌 disclosure로 렌더링한다', () => {
  assert.match(source, /<details className="metric-disclosure" key=\{resultId\}>/);
  assert.doesNotMatch(source, /<details[^>]+\sopen(?:=|\s|>)/);
  assert.match(source, /상세 분석 지표 보기/);
  assert.match(source, /상세 분석 지표 숨기기/);
});

test('Raw Metrics는 disclosure 내부에서만 렌더링한다', () => {
  const disclosureStart = source.indexOf('<details className="metric-disclosure"');
  const disclosureEnd = source.indexOf('</details>', disclosureStart);
  const metricPanel = source.indexOf('<section className="metric-panel">');

  assert.ok(disclosureStart >= 0);
  assert.ok(metricPanel > disclosureStart);
  assert.ok(metricPanel < disclosureEnd);
});
