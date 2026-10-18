import assert from 'node:assert/strict';
import test, { beforeEach } from 'node:test';
import * as api from './adminMockApi.js';

// 목은 로그인 여부를 sessionStorage에 둔다. node에는 없어서 메모리 저장소로 대신한다
const store = new Map();
globalThis.sessionStorage = {
  getItem: (key) => store.get(key) ?? null,
  setItem: (key, value) => store.set(key, String(value)),
  removeItem: (key) => store.delete(key),
};

const source = (overrides = {}) => ({ courtLevel: 'FIRST', caseNumber: '2099고합900', courtName: '가상지방법원', decidedAt: '2025-03-01', isFinal: true, sourceOrg: '법제처', note: null, originalText: '가상 원문', ...overrides });
const request = (overrides = {}) => ({ crimeType: 'MURDER', sources: [source()], ...overrides });
const code = (promise) => promise.then(() => null, (failure) => failure.code);

async function runToEnd(importId) {
  for (let i = 0; i < 20; i += 1) {
    const view = await api.getImport(importId);
    if (view.status === 'SUCCEEDED' || view.status === 'FAILED') return view;
  }
  throw new Error('등록이 끝나지 않았다');
}

beforeEach(async () => {
  store.clear();
  api.resetAdminMock();
  await api.login('admin@example.com', 'pw');
});

test('로그인하지 않으면 등록할 수 없다', async () => {
  store.clear();
  assert.equal(await code(api.createImport(request())), 'UNAUTHORIZED');
  assert.equal(await code(api.getImport(1)), 'UNAUTHORIZED');
});

test('등록 요청 검증: 범죄 유형 · 판결문 수 · 심급 중복 · 사건번호 · 원문', async () => {
  assert.equal(await code(api.createImport(request({ crimeType: 'THEFT' }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source(), source({ caseNumber: 'a' })] }))), 'VALIDATION_ERROR'); // 같은 심급
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '' })] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source({ originalText: '  ' })] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source({ decidedAt: '2025/03/01' })] }))), 'VALIDATION_ERROR');
});

test('이미 등록한 사건번호는 거절한다', async () => {
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '2099고합000' })] }))), 'DUPLICATE_SOURCE');
});

test('등록하면 단계가 차례로 끝나고 DRAFT 사건이 생긴다', async () => {
  const { importId, status } = await api.createImport(request());
  assert.equal(status, 'QUEUED');
  const first = await api.getImport(importId);
  assert.equal(first.status, 'RUNNING');
  assert.equal(first.stages[0].status, 'RUNNING');
  assert.equal(first.stages.at(-1).status, 'PENDING');

  const done = await runToEnd(importId);
  assert.equal(done.status, 'SUCCEEDED');
  assert.ok(done.stages.every((stage) => stage.status === 'DONE'));
  const created = await api.getCase(done.caseId);
  assert.equal(created.status, 'DRAFT');
  assert.equal(created.sources[0].caseNumber, '2099고합900');
  assert.equal(created.candidates.AI.length, 1);
});

test('실행 중에는 다른 등록을 시작할 수 없다', async () => {
  await api.createImport(request());
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '2099고합901' })] }))), 'IMPORT_IN_PROGRESS');
});

test('원문에 [실패]가 있으면 비식별화 단계에서 실패한다', async () => {
  const { importId } = await api.createImport(request({ sources: [source({ originalText: '[실패] 원문' })] }));
  const view = await runToEnd(importId);
  assert.equal(view.status, 'FAILED');
  assert.equal(view.stages[0].status, 'FAILED');
  assert.equal(view.caseId, null);
  assert.ok(view.message);
});

test('없는 등록 작업은 IMPORT_NOT_FOUND', async () => {
  assert.equal(await code(api.getImport(999)), 'IMPORT_NOT_FOUND');
});
