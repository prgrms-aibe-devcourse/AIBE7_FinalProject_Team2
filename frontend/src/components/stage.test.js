// npm test : 무대 대본 진행 로직(모습 이어받기 · 대사 건너뛰기 경계)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { flatten, skipTarget } from './stage.js';

const script = JSON.parse(readFileSync(new URL('../content/case-1/script.json', import.meta.url)));
const steps = flatten(script.acts, ['1', '2']);

test('장면은 앞 장면의 모습 · 이름표를 이어받는다', () => {
  const actor = steps.findIndex((s) => s.look.nambul === 'role:actor');
  assert.equal(steps[actor + 1].look.naero, 'role:actor');
  assert.equal(steps[actor + 1].tags.nambul, 'A씨 역');
  assert.equal(steps.at(-1).tags.nambul, '', '2막은 이름표를 새로 시작한다');
});

test('건너뛰기는 같은 막의 다음 카드 · 막 끝에서 멈추고 다음 막으로 넘어가지 않는다', () => {
  assert.equal(steps[skipTarget(steps, 0)].card, 'overview');
  const lastOfAct1 = steps.findLastIndex((s) => s.actId === '1');
  assert.equal(skipTarget(steps, 3), lastOfAct1);
  assert.equal(steps[skipTarget(steps, lastOfAct1 + 1)].card, 'preJudgment');
  assert.equal(skipTarget(steps, steps.length - 1), steps.length - 1);
});
