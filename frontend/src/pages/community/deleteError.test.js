// npm test : 내 글 삭제 실패 문구 (FE-165)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DELETE_ERRORS, DELETE_FAILED, deleteErrorText } from './deleteError.js';

test('에러 코드마다 고칠 방법을 알리는 문구가 나온다(401 · 403 · 404)', () => {
  assert.equal(deleteErrorText({ code: 'UNAUTHORIZED' }), DELETE_ERRORS.UNAUTHORIZED);
  assert.equal(deleteErrorText({ code: 'FORBIDDEN' }), '내 글만 지울 수 있어요.');
  assert.equal(deleteErrorText({ code: 'POST_NOT_FOUND' }), DELETE_ERRORS.POST_NOT_FOUND);
});

test('모르는 코드 · 네트워크 오류 · 빈 값은 일반 실패 문구이고 터지지 않는다', () => {
  assert.equal(deleteErrorText({ code: 'NETWORK_ERROR' }), DELETE_FAILED);
  assert.equal(deleteErrorText({ code: 'toString' }), DELETE_FAILED, '객체 기본 속성 이름이 코드로 와도 문구가 아니라 일반 실패');
  assert.equal(deleteErrorText(undefined), DELETE_FAILED);
  assert.equal(deleteErrorText(null), DELETE_FAILED);
});
