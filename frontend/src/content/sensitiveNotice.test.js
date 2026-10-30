import { test } from 'node:test';
import assert from 'node:assert/strict';
import { sensitiveNotice } from './sensitiveNotice.js';

test('플래그가 없거나 안내 대상이 아니면 빈 문자열', () => {
  assert.equal(sensitiveNotice(undefined), '');
  assert.equal(sensitiveNotice(null), '');
  assert.equal(sensitiveNotice([]), '');
  assert.equal(sensitiveNotice(['CAPITAL_PENALTY', 'OVERSEAS']), ''); // 가드 전용은 서버가 주지 않지만 와도 안내하지 않는다
});

test('한 가지: 받침에 맞게 과/와를 붙인다', () => {
  assert.equal(sensitiveNotice(['MENTAL_HEALTH']), '이 사건에는 정신건강과 관련된 내용이 나와요.');
  assert.equal(sensitiveNotice(['VULNERABLE_VICTIM']), '이 사건에는 어린 · 고령 · 장애 피해자와 관련된 내용이 나와요.');
  assert.equal(sensitiveNotice(['SELF_HARM']), '이 사건에는 자해 · 자살과 관련된 내용이 나와요.');
  assert.equal(sensitiveNotice(['SEXUAL_OFFENSE']), '이 사건에는 성범죄와 관련된 내용이 나와요.');
});

test('여러 가지: 쉼표로 잇고 마지막에만 조사를 붙인다 · 모르는 값은 건너뛴다', () => {
  assert.equal(sensitiveNotice(['VULNERABLE_VICTIM', 'MENTAL_HEALTH', 'SELF_HARM']),
    '이 사건에는 어린 · 고령 · 장애 피해자, 정신건강, 자해 · 자살과 관련된 내용이 나와요.');
  assert.equal(sensitiveNotice(['UNKNOWN', 'HATE_CRIME']), '이 사건에는 혐오 · 차별과 관련된 내용이 나와요.');
});
