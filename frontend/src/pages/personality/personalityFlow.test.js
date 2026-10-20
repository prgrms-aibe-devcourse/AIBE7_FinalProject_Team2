// npm test : 판결 성향 테스트 진행 규칙 (FE-55, personalityFlow.js). /test와 /test/paper가 같은 규칙으로 움직이는지 고정한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createTestFlow } from './personalityFlow.js';

const QUESTIONS = [{ id: 1 }, { id: 2 }, { id: 3 }];
const sleep = (ms) => new Promise((resolve) => { setTimeout(resolve, ms); });

function makeFlow({ active = true, submitPersonality = async () => {} } = {}) {
  const flow = createTestFlow({ api: { submitPersonality }, isActive: () => active });
  flow.setQuestions(QUESTIONS);
  return flow;
}

test('앞 문항을 모두 답한 다음 문항까지만 갈 수 있다(건너뛰기 없음)', () => {
  const flow = makeFlow();
  flow.enter(0);
  assert.equal(flow.canReach(0, 1), false);
  flow.answer(0, 2, {});
  assert.equal(flow.canReach(0, 1), true);
  assert.equal(flow.canReach(0, 2), false);
  assert.equal(flow.canReach(1, 0), true); // 앞 문항으로는 언제든 돌아간다
});

test('처음 답한 문항이면 true, 답을 바꾸면 false', () => {
  const flow = makeFlow();
  flow.enter(0);
  assert.equal(flow.answer(0, 1, {}), true);
  assert.equal(flow.answer(0, 3, {}), false);
  assert.equal(flow.answers.get(1), 3);
});

test('누르거나 숫자 키로 고르면 잠시 뒤 다음 문항으로, 화살표(advance 없음)는 넘어가지 않는다', async () => {
  const flow = makeFlow();
  const shown = [];
  const goNext = () => flow.next(0, { show: (i) => shown.push(i), submit: () => shown.push('submit') });
  flow.enter(0);
  flow.answer(0, 4, { goNext });
  await sleep(320);
  assert.deepEqual(shown, []);
  flow.answer(0, 4, { advance: true, goNext });
  await sleep(320);
  assert.deepEqual(shown, [1]);
});

test('다른 문항으로 옮기면 기다리던 자동 넘기기는 취소된다', async () => {
  const flow = makeFlow();
  const shown = [];
  flow.enter(0);
  flow.answer(0, 0, { advance: true, goNext: () => flow.next(0, { show: (i) => shown.push(i), submit() {} }) });
  flow.enter(0); // 같은 문항을 다시 그림(예: 이전 → 다시)
  await sleep(320);
  assert.deepEqual(shown, []);
});

test('표지로 돌아가면 기다리던 자동 넘기기는 취소된다', async () => {
  const flow = makeFlow();
  const shown = [];
  flow.enter(0);
  flow.answer(0, 0, { advance: true, goNext: () => flow.next(0, { show: (i) => shown.push(i), submit() {} }) });
  flow.leave(); // 칸을 누른 직후 "이전"으로 표지에 감
  await sleep(320);
  assert.deepEqual(shown, []);
});

test('마지막 문항은 자동으로 넘기지 않고, 다음을 누르면 제출한다', async () => {
  const flow = makeFlow();
  const calls = [];
  const last = QUESTIONS.length - 1;
  flow.enter(last);
  flow.answer(last, 2, { advance: true, goNext: () => calls.push('auto') });
  await sleep(320);
  assert.deepEqual(calls, []);
  flow.next(last, { show: () => calls.push('show'), submit: () => calls.push('submit') });
  assert.deepEqual(calls, ['submit']);
});

test('답하지 않았거나 화면을 떠났으면 다음으로 가지 않는다', () => {
  const calls = [];
  const flow = makeFlow();
  flow.enter(0);
  flow.next(0, { show: () => calls.push('show'), submit() {} });
  const gone = makeFlow({ active: false });
  gone.enter(0);
  gone.answer(0, 1, {});
  gone.next(0, { show: () => calls.push('show'), submit() {} });
  assert.deepEqual(calls, []);
});

test('제출은 문항 순서대로 답을 보내고, 실패하면 그대로 throw', async () => {
  let sent;
  const flow = makeFlow({ submitPersonality: async (answers) => { sent = answers; } });
  QUESTIONS.forEach((_, i) => flow.answer(i, i, {}));
  await flow.submit();
  assert.deepEqual(sent, [{ questionId: 1, value: 0 }, { questionId: 2, value: 1 }, { questionId: 3, value: 2 }]);
  const failing = makeFlow({ submitPersonality: async () => { throw { code: 'SERVER_ERROR' }; } });
  await assert.rejects(failing.submit(), { code: 'SERVER_ERROR' });
});
