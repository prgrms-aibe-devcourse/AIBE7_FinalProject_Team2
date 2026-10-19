import assert from 'node:assert/strict';
import test from 'node:test';
import { errorText } from './adminLabels.js';

test('errorText: IMPORT_NOT_FOUND는 범용 _NOT_FOUND보다 먼저 한글 문구로 바뀐다', () => {
  assert.equal(errorText({ code: 'IMPORT_NOT_FOUND', message: 'import not found' }), '등록 작업을 찾을 수 없어요.');
});

test('errorText: 그 밖의 _NOT_FOUND는 서버 메시지를 그대로 쓴다', () => {
  assert.equal(errorText({ code: 'CASE_NOT_FOUND', message: '사건을 찾을 수 없어요.' }), '사건을 찾을 수 없어요.');
});
