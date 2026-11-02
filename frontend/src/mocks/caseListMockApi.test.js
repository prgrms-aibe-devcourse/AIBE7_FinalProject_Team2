// npm test : API 3 내 체험 상태 목 (FE-58, S-09 리뷰 페이지 진입 확인)
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { getCaseDetail, getCaseScript, getCases, getExperience, startExperience } from './caseListMockApi.js';
import { save as saveExperience, storageKey } from './mockExperienceStore.js';
import { LOCKED_CASE_ID } from './caseListMockApi.js';

// node에는 localStorage가 없어 테스트마다 빈 저장소를 둔다
beforeEach(() => {
  const data = new Map();
  globalThis.localStorage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: (key) => data.delete(key) };
});

test('체험 기록이 없으면 EXPERIENCE_NOT_FOUND, 기록을 새로 만들지 않는다', async () => {
  await assert.rejects(getExperience(1), { code: 'EXPERIENCE_NOT_FOUND' });
  assert.equal(localStorage.getItem(storageKey), null);
  await assert.rejects(getExperience(2), { code: 'EXPERIENCE_NOT_FOUND' });
});

test('기록이 있으면 지금 상태를 돌려준다', async () => {
  saveExperience({ status: 'COMPLETED', lastReviewedStep: 4 });
  assert.equal((await getExperience(1)).status, 'COMPLETED');
  await startExperience(3);
  assert.equal((await getExperience(3)).status, 'STARTED');
});

test('없는 사건 번호는 CASE_NOT_FOUND', async () => {
  await assert.rejects(getExperience(999999), { code: 'CASE_NOT_FOUND' });
});

test('scriptReady: 대본이 있는 사건 1만 true이고 목록 · 상세가 같다 (BE-116)', async () => {
  const { cases } = await getCases();
  assert.deepEqual(cases.filter((item) => item.scriptReady).map((item) => item.caseId), [1, 3, 5]); // 3은 scriptStatus 없는 점검 사건(FE-145), 5는 잠긴 사건(FE-149, 대본은 준비됨)
  assert.equal((await getCaseDetail(1)).scriptReady, true);
  assert.equal((await getCaseDetail(2)).scriptReady, false);
});

test('API 16 목: 안내 대상 플래그는 사건 4에만 있고 나머지는 필드를 뺀다(FE-129)', async () => {
  assert.deepEqual((await getCaseDetail(4)).sensitiveFlags, ['MENTAL_HEALTH']);
  assert.equal('sensitiveFlags' in (await getCaseDetail(1)), false);
});

test('API 18 목: 사건 1만 블록을 주고, U0 · 검수용 값은 없으며, 대본 없는 사건 · 없는 사건은 명세의 에러다', async () => {
  const { blocks, meta } = await getCaseScript(1);
  assert.deepEqual(Object.keys(blocks).sort(), ['U1', 'U2', 'U3', 'U4']);
  assert.deepEqual(Object.keys(blocks.U3[0]).sort(), ['factorId', 'role', 'text']);
  assert.deepEqual(meta, { cast: { prosecutor: 'naero', defense: 'nambul' }, issue: 'admitted', admits: true });
  await assert.rejects(getCaseScript(4), { code: 'SCRIPT_NOT_FOUND' });
  await assert.rejects(getCaseScript(99999), { code: 'CASE_NOT_FOUND' });
});

test('사건 2는 점검 중: 목록 · 상세에 MAINTENANCE로 보이고 API 18은 503 코드다 (FE-145)', async () => {
  const { cases } = await getCases();
  assert.equal(cases.find((item) => item.caseId === 2).scriptStatus, 'MAINTENANCE');
  assert.equal(cases.find((item) => item.caseId === 1).scriptStatus, 'READY');
  assert.equal((await getCaseDetail(2)).scriptStatus, 'MAINTENANCE');
  await assert.rejects(getCaseScript(2), { code: 'SCRIPT_UNDER_MAINTENANCE' });
});

test('사건 3은 scriptStatus 없이 scriptReady true인데 API 18이 503이다 (FE-145)', async () => {
  const detail = await getCaseDetail(3);
  assert.equal(detail.scriptReady, true);
  assert.equal('scriptStatus' in detail, false);
  await assert.rejects(getCaseScript(3), { code: 'SCRIPT_UNDER_MAINTENANCE' });
});

test('순차 해금 목(BE-146, FE-149): 사건 5가 잠겨 있고 시작은 409 CASE_LOCKED, 사건 1을 마치면 열린다', async () => {
  const { cases, unlock } = await getCases();
  assert.deepEqual(unlock, { completedCases: 0, nextStepAt: 1 });
  assert.deepEqual(cases.filter((item) => item.locked).map((item) => item.caseId), [LOCKED_CASE_ID]);
  assert.equal(cases.find((item) => item.caseId === LOCKED_CASE_ID).scriptStatus, 'READY', '잠김은 준비 중이 아니다');
  assert.equal((await getCaseDetail(LOCKED_CASE_ID)).locked, true);
  assert.equal((await getCaseDetail(1)).locked, false);
  await assert.rejects(startExperience(LOCKED_CASE_ID), { code: 'CASE_LOCKED' });

  saveExperience({ status: 'COMPLETED', lastReviewedStep: 4 }); // 사건 1 완료
  await startExperience(1);
  const after = await getCases();
  assert.deepEqual(after.unlock, { completedCases: 1, nextStepAt: 3 });
  assert.equal(after.cases.some((item) => item.locked), false);
  assert.equal((await startExperience(LOCKED_CASE_ID)).caseId, LOCKED_CASE_ID);
});
