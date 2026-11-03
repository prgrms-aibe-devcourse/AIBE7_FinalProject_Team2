import assert from 'node:assert/strict';
import test from 'node:test';
// 목은 로그인 여부를 sessionStorage에 둔다. node에는 없어서 메모리 저장소로 대신한다
const store = new Map();
globalThis.sessionStorage = { getItem: (k) => store.get(k) ?? null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };

import { PUBLISH_REASON } from './adminLabels.js';
import { confirmSpecialMode, csrf, getCase, login, publishCase, resetAdminMock } from './adminMockApi.js';

// BE-165 · FE-163: 특별 모드로 적재한 사건은 경고를 확인(A-13-22)해야 공개 조건이 풀린다. 목이 서버 계약(API 명세 v0.95)과 같은 동작을 한다
async function signIn() {
  resetAdminMock();
  await csrf();
  await login('admin@example.com', 'admin-password');
}

test('특별 모드 사건은 SPECIAL_MODE_UNREVIEWED가 공개 불가 사유에 있고, 문구가 있다', async () => {
  await signIn();
  const detail = await getCase(12);
  assert.ok(detail.publishReadiness.reasons.includes('SPECIAL_MODE_UNREVIEWED'));
  assert.ok(PUBLISH_REASON.SPECIAL_MODE_UNREVIEWED);
  await assert.rejects(() => publishCase(12), (error) => error.details.some((item) => item.reason === 'SPECIAL_MODE_UNREVIEWED'));
});

test('경고를 확인하면 사유가 사라지고, 다시 눌러도 그대로다', async () => {
  await signIn();
  const confirmed = await confirmSpecialMode(12);
  assert.ok(!confirmed.publishReadiness.reasons.includes('SPECIAL_MODE_UNREVIEWED'));
  const again = await confirmSpecialMode(12);
  assert.deepEqual(again.publishReadiness, confirmed.publishReadiness);
});

test('특별 모드가 아닌 사건에 눌러도 바꾸지 않는다', async () => {
  await signIn();
  const before = await getCase(13);
  const after = await confirmSpecialMode(13);
  assert.deepEqual(after.publishReadiness, before.publishReadiness);
});
