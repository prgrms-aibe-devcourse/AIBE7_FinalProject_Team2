import assert from 'node:assert/strict';
import test from 'node:test';
import { errorText, formatDate, formatDateTime } from './adminLabels.js';

test('errorText: IMPORT_NOT_FOUND는 범용 _NOT_FOUND보다 먼저 한글 문구로 바뀐다', () => {
  assert.equal(errorText({ code: 'IMPORT_NOT_FOUND', message: 'import not found' }), '등록 작업을 찾을 수 없어요.');
});

test('errorText: 적재 거절(IMPORT_RESULT_INVALID)은 문제가 된 값의 경로를 함께 보여 준다', () => {
  const text = errorText({ code: 'IMPORT_RESULT_INVALID', details: [{ field: 'case.factors[2].label' }, { field: 'report.factorExtras[factorId=5].summaryTag' }] });
  assert.match(text, /다시 생성해 주세요/);
  assert.match(text, /case\.factors\[2\]\.label/);
  assert.match(text, /report\.factorExtras\[factorId=5\]\.summaryTag/);
});

test('errorText: 경로가 없는 IMPORT_RESULT_INVALID는 괄호를 붙이지 않는다', () => {
  assert.equal(errorText({ code: 'IMPORT_RESULT_INVALID' }), '생성 결과를 사건으로 만들 수 없어요. 다시 생성해 주세요.');
});

test('errorText: 적재 전 상태 · 파이프라인 중단도 한글 안내가 있다', () => {
  assert.match(errorText({ code: 'IMPORT_NOT_READY' }), /아직 적재할 수 없어요/);
  assert.match(errorText({ code: 'IMPORT_UNAVAILABLE' }), /지금 쓸 수 없어요/);
});

test('errorText: 그 밖의 _NOT_FOUND는 서버 메시지를 그대로 쓴다', () => {
  assert.equal(errorText({ code: 'CASE_NOT_FOUND', message: '사건을 찾을 수 없어요.' }), '사건을 찾을 수 없어요.');
});

test('formatDate · formatDateTime: 빈 값은 —, 읽을 수 없는 값은 그대로, 날짜만 · 시각까지 같은 기준으로 만든다', () => {
  assert.equal(formatDate(null), '—');
  assert.equal(formatDateTime(''), '—');
  assert.equal(formatDate('날짜 아님'), '날짜 아님');
  const stamp = new Date(2025, 0, 9, 7, 5).toISOString();
  assert.equal(formatDate(stamp), '2025-01-09');
  assert.equal(formatDateTime(stamp), '2025-01-09 07:05');
});
