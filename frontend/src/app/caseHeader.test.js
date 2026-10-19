// npm test : 사건 상단 바 공급원(BE-79) — 목록(API 1)에서 제목 · 죄명을 찾는 규칙
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCaseHeader, rememberFromList, rememberFromOverview } from './caseHeader.js';

// cache가 모듈 전역이라 테스트마다 다른 caseId를 쓴다

test('목록의 제목 · 죄명으로 상단 바를 채운다', async () => {
  const api = { getCases: async () => ({ cases: [{ caseId: 101, title: '제목', chargeName: '횡령' }] }) };
  assert.deepEqual(await loadCaseHeader(api, 101), { caseId: 101, title: '제목', chargeName: '횡령' });
});

test('목록에 없는 사건은 null (라우터가 404로 보낸다)', async () => {
  const api = { getCases: async () => ({ cases: [] }) };
  assert.equal(await loadCaseHeader(api, 999), null);
});

test('목록 조회가 실패하면 대체 제목을 돌려준다', async () => {
  const api = { getCases: async () => { throw new Error('network'); } };
  assert.deepEqual(await loadCaseHeader(api, 998), { caseId: 998, title: '사건 998', chargeName: '' });
});

test('목록에 죄명이 없어도 개요에서 받은 죄명은 지워지지 않는다', async () => {
  rememberFromOverview({ case: { caseId: 102, title: '제목', chargeName: '사기' } });
  rememberFromList({ cases: [{ caseId: 102, title: '제목' }] });
  const header = await loadCaseHeader({ getCases: async () => ({ cases: [] }) }, 102);
  assert.equal(header.chargeName, '사기');
});
