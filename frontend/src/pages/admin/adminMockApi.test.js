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
  assert.equal(await code(api.createImport(request({ crimeType: 'ARSON' }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source(), source({ caseNumber: 'a' })] }))), 'VALIDATION_ERROR'); // 같은 심급
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '' })] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source({ originalText: '  ' })] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source({ decidedAt: '2025/03/01' })] }))), 'VALIDATION_ERROR');
});

test('이미 등록한 사건번호는 거절한다', async () => {
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '2099고합000' })] }))), 'DUPLICATE_SOURCE');
});

test('등록하면 단계가 차례로 끝나지만 생성만으로는 사건이 생기지 않는다', async () => {
  const { importId, status } = await api.createImport(request());
  assert.equal(status, 'QUEUED');
  const first = await api.getImport(importId);
  assert.equal(first.status, 'RUNNING');
  assert.equal(first.stages[0].status, 'RUNNING');
  assert.equal(first.stages.at(-1).status, 'SKIPPED'); // load는 서버가 A-14-1로 한다

  const done = await runToEnd(importId);
  assert.equal(done.status, 'SUCCEEDED');
  // 늘 건너뛰는 세 단계(contamination · rag · load)를 빼면 모두 끝나 있다
  const skipped = done.stages.filter((stage) => stage.status === 'SKIPPED').map((stage) => stage.name);
  assert.deepEqual(skipped, ['contamination', 'rag', 'load']);
  assert.ok(done.stages.filter((stage) => !skipped.includes(stage.name)).every((stage) => stage.status === 'DONE'));
  assert.equal(done.caseId, null); // 적재(A-14-1) 전에는 사건이 없다
});

test('적재하면 DRAFT 사건과 판결 후보가 생기고 caseId가 채워진다', async () => {
  const { importId } = await api.createImport(request());
  await runToEnd(importId);
  const loaded = await api.loadImport(importId);
  assert.equal(loaded.status, 'SUCCEEDED');
  assert.ok(loaded.caseId);
  const created = await api.getCase(loaded.caseId);
  assert.equal(created.status, 'DRAFT');
  assert.equal(created.sources[0].caseNumber, '2099고합900');
  assert.equal(created.candidates.AI.length, 1);
  // 조회(A-15)도 같은 caseId를 그대로 돌려준다
  assert.equal((await api.getImport(importId)).caseId, loaded.caseId);
});

test('생성이 끝나지 않았거나 이미 적재한 등록은 IMPORT_NOT_READY', async () => {
  const { importId } = await api.createImport(request());
  assert.equal(await code(api.loadImport(importId)), 'IMPORT_NOT_READY'); // 아직 생성 중
  await runToEnd(importId);
  await api.loadImport(importId);
  assert.equal(await code(api.loadImport(importId)), 'IMPORT_NOT_READY'); // 두 번 적재하지 않는다
});

test('적재 거절: 제목이 겹치면 DUPLICATE_TITLE, 생성 결과가 잘못되면 IMPORT_RESULT_INVALID', async () => {
  const dup = await api.createImport(request({ sources: [source({ originalText: '[제목중복] 원문' })] }));
  await runToEnd(dup.importId);
  assert.equal(await code(api.loadImport(dup.importId)), 'DUPLICATE_TITLE');

  const bad = await api.createImport(request({ sources: [source({ caseNumber: '2099고합902', originalText: '[적재실패] 원문' })] }));
  await runToEnd(bad.importId);
  const failure = await api.loadImport(bad.importId).catch((error) => error);
  assert.equal(failure.code, 'IMPORT_RESULT_INVALID');
  assert.ok(failure.details?.[0]?.field); // 어느 값이 문제인지 화면에 그대로 쓴다
});

test('적재 뒤 다시 적재하면 IMPORT_NOT_READY지만 조회에는 caseId가 남는다 (화면이 완료로 잇는 근거, PR #285 리뷰)', async () => {
  const { importId } = await api.createImport(request());
  await runToEnd(importId);
  const { caseId } = await api.loadImport(importId);
  assert.equal(await code(api.loadImport(importId)), 'IMPORT_NOT_READY');
  assert.equal((await api.getImport(importId)).caseId, caseId);
});

test('생성 결과가 사라지면 IMPORT_NOT_READY를 사유(message)와 함께 돌려준다', async () => {
  const { importId } = await api.createImport(request({ sources: [source({ originalText: '[결과소실] 원문' })] }));
  await runToEnd(importId);
  const failure = await api.loadImport(importId).catch((error) => error);
  assert.equal(failure.code, 'IMPORT_NOT_READY');
  assert.match(failure.message, /다시 등록/);
  assert.equal((await api.getImport(importId)).caseId, null); // 적재도 안 됐고 생성은 끝난 상태
});

test('없는 등록 작업은 적재도 IMPORT_NOT_FOUND', async () => {
  assert.equal(await code(api.loadImport(999)), 'IMPORT_NOT_FOUND');
});

test('로그인하지 않으면 적재할 수 없다', async () => {
  const { importId } = await api.createImport(request());
  await runToEnd(importId);
  store.clear();
  assert.equal(await code(api.loadImport(importId)), 'UNAUTHORIZED');
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
