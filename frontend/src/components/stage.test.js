// npm test : 무대 대본 진행 로직(모습 · 이름표 이어받기, 대사 건너뛰기 경계, 서버 확인 장면)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { flatten, skipTarget } from './stage.js';

const script = JSON.parse(readFileSync(new URL('../content/case-1/script.json', import.meta.url)));
const steps = flatten(script.acts, ['1', '2']);
const trial = flatten(script.acts, ['3']);

test('장면은 앞 장면의 모습 · 이름표를 이어받는다', () => {
  const actor = steps.findIndex((s) => s.look.nambul === 'role:actor');
  assert.equal(steps[actor + 1].look.naero, 'role:actor');
  assert.equal(steps[actor + 1].chips.nambul, 'A씨 역');
  assert.equal(steps.at(-1).chips.nambul, '', '평상복으로 돌아오면 이름표가 없다');
  assert.equal(trial[0].chips.naero, '변호인', '역할 의상이면 의상 이름이 이름표가 된다');
});

test('건너뛰기는 같은 막의 다음 보드 · 막 끝에서 멈추고 다음 막으로 넘어가지 않는다', () => {
  const lastOfAct1 = steps.findLastIndex((s) => s.actId === '1');
  assert.equal(skipTarget(steps, 0), lastOfAct1, '1막에는 보드가 없어 막 끝까지 간다');
  assert.equal(steps[skipTarget(steps, lastOfAct1 + 1)].card, 'preJudgment');
});

test('건너뛰기는 서버 확인(confirm) 장면을 넘지 않는다', () => {
  const confirms = trial.flatMap((s, i) => (s.confirm ? [i] : []));
  assert.deepEqual(confirms.map((i) => trial[i].confirm), [2, 3, 4]);
  for (let i = 0; i < trial.length; i += 1) {
    const to = skipTarget(trial, i);
    assert.ok(!confirms.some((c) => c >= i && c < to), `${i} → ${to} 사이에 confirm 장면이 있다`);
  }
});

test('재진입: 서버가 연 섹션의 첫 장면이 있다(② · ③ · ④)', () => {
  [2, 3, 4].forEach((n) => assert.ok(trial.some((s) => s.section === n), `섹션 ${n}`));
});
