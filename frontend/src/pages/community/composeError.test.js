// npm test : 글쓰기 실패 문구 (FE-137)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SESSION_EXPIRED, VERDICT_NOT_CONFIRMED, composeErrorText } from './composeError.js';

test('로그인이 풀리면(401) 다시 로그인으로 안내한다', () => {
  assert.equal(composeErrorText({ code: 'UNAUTHORIZED', status: 401 }), SESSION_EXPIRED);
});

test('판결 공유인데 확정한 판결이 없으면 그 이유를 바로 알린다(C-3 shareVerdict)', () => {
  const failure = { code: 'VALIDATION_ERROR', details: [{ field: 'shareVerdict', reason: 'VERDICT_NOT_CONFIRMED' }] };
  assert.equal(composeErrorText(failure), VERDICT_NOT_CONFIRMED);
});

test('다른 입력 오류는 입력 확인 문구, 칸 정보가 없어도 터지지 않는다', () => {
  assert.equal(composeErrorText({ code: 'VALIDATION_ERROR', details: [{ field: 'title', reason: 'MUST_BE_1_TO_60_CHARS' }] }), '입력한 내용을 확인해 주세요.');
  assert.equal(composeErrorText({ code: 'VALIDATION_ERROR', details: null }), '입력한 내용을 확인해 주세요.');
  assert.equal(composeErrorText({ code: 'VALIDATION_ERROR' }), '입력한 내용을 확인해 주세요.');
});

test('그 밖의 실패(연결 · 서버)는 잠시 뒤 다시 시도로 안내한다', () => {
  assert.equal(composeErrorText({ code: 'NETWORK_ERROR' }), '글을 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.');
  assert.equal(composeErrorText(undefined), '글을 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.');
});
