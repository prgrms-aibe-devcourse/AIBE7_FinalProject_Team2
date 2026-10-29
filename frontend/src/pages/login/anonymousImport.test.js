import assert from 'node:assert/strict';
import { test } from 'node:test';
import { importSummary, offerAnonymousImport } from './anonymousImport.js';

const spyApi = (candidate) => {
  const calls = [];
  return { calls, api: { getAnonymousImport: async () => candidate, importAnonymous: async () => calls.push('import'), discardAnonymous: async () => calls.push('discard'), logout: async () => calls.push('logout') } };
};

test('가져올 기록 문구: 0건인 것은 뺀다', () => {
  assert.equal(importSummary({ experienceCount: 3, preJudgmentCount: 2 }), '체험 기록 3건 · 첫인상 엽서 2건');
  assert.equal(importSummary({ experienceCount: 0, preJudgmentCount: 2 }), '첫인상 엽서 2건');
  assert.equal(importSummary({ experienceCount: 1 }), '체험 기록 1건');
  assert.equal(importSummary({}), '');
  assert.equal(importSummary(null), '');
});

test('후보가 없으면 창을 띄우지 않는다: 없음 · 빈 기록 · 조회 실패 · 서버에 함수가 없음', async () => {
  const shown = [];
  const show = async (candidate) => shown.push(candidate);
  assert.equal(await offerAnonymousImport(spyApi(null).api, show), 'none');
  await offerAnonymousImport(spyApi({ experienceCount: 0, preJudgmentCount: 0 }).api, show);
  await offerAnonymousImport({ getAnonymousImport: async () => { throw { status: 500 }; } }, show);
  await offerAnonymousImport({}, show);
  assert.deepEqual(shown, []);
});

test('후보가 있으면 창에 후보와 두 동작을 넘기고, 고를 때까지 기다린다', async () => {
  const { api, calls } = spyApi({ experienceCount: 2, preJudgmentCount: 1 });
  let received;
  let finished = false;
  const done = offerAnonymousImport(api, async (candidate, actions) => {
    received = candidate;
    await new Promise((resolve) => setTimeout(resolve, 5));
    await actions.importIt();
    finished = true;
    return 'imported';
  }).then(() => assert.equal(finished, true));
  await done;
  assert.deepEqual(received, { experienceCount: 2, preJudgmentCount: 1 });
  assert.deepEqual(calls, ['import']);
  const second = spyApi({ experienceCount: 1 });
  await offerAnonymousImport(second.api, async (_candidate, actions) => actions.discard());
  assert.deepEqual(second.calls, ['discard']);
});

test('닫기(decline)는 로그아웃하고 익명 기록은 건드리지 않는다. 로그아웃이 실패하면 던진다', async () => {
  const { api, calls } = spyApi({ experienceCount: 1 });
  const result = await offerAnonymousImport(api, async (_candidate, actions) => { await actions.decline(); return 'declined'; });
  assert.equal(result, 'declined');
  assert.deepEqual(calls, ['logout']);
  const failing = { ...api, logout: async () => { throw { status: 500 }; } };
  await assert.rejects(offerAnonymousImport(failing, (_candidate, actions) => actions.decline()), { status: 500 });
});
