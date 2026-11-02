import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCaseScript, loadFailText } from './caseScript.js';
import { getCaseScript } from '../mocks/caseListMockApi.js';

const failing = (code) => async () => { throw { code }; };

test('SCRIPT_NOT_FOUND면 null이고 기억하지 않아, 승인되면 다음 요청에 받는다', async () => {
  assert.equal(await loadCaseScript({ getCaseScript: failing('SCRIPT_NOT_FOUND') }, 101), null);
  const script = await loadCaseScript({ getCaseScript: () => getCaseScript(1) }, 101);
  assert.ok(script.acts['2']);
});

test('받은 대본은 기억해 다시 요청하지 않는다(체험 중 점검 · 수정과 무관하게 끝까지)', async () => {
  const first = await loadCaseScript({ getCaseScript: () => getCaseScript(1) }, 102);
  const again = await loadCaseScript({ getCaseScript: failing('SCRIPT_UNDER_MAINTENANCE') }, 102);
  assert.equal(again, first);
});

test('그 밖의 오류는 화면으로 던지고, 조립 못 하는 응답은 null이다', async () => {
  await assert.rejects(loadCaseScript({ getCaseScript: failing('INTERNAL') }, 103), { code: 'INTERNAL' });
  assert.equal(await loadCaseScript({ getCaseScript: async () => ({ evidence: [], cast: {} }) }, 104), null);
});

test('503 점검 중은 준비 중(null)과 달리 던지고, 기억하지 않는다 (FE-145)', async () => {
  await assert.rejects(loadCaseScript({ getCaseScript: failing('SCRIPT_UNDER_MAINTENANCE') }, 105), { code: 'SCRIPT_UNDER_MAINTENANCE' });
  assert.ok(await loadCaseScript({ getCaseScript: () => getCaseScript(1) }, 105));
});

test('불러오기 실패 안내: 점검 중이면 점검 문구, 그 밖에는 일반 문구 (FE-145)', () => {
  assert.match(loadFailText({ code: 'SCRIPT_UNDER_MAINTENANCE' }), /점검/);
  assert.doesNotMatch(loadFailText({ code: 'INTERNAL' }), /점검/);
});
