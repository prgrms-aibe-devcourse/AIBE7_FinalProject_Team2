// npm test : 무대 대본 진행 로직(모습 · 이름표 이어받기, 대사 건너뛰기 경계, 서버 확인 장면)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { flatten, skipTarget } from './stage.js';

const script = JSON.parse(readFileSync(new URL('../content/case-1/script.json', import.meta.url)));
const steps = flatten(script.acts, ['1', '2']);
const trial = flatten(script.acts, ['3']);

test('장면은 앞 장면의 모습 · 이름표를 이어받는다', () => {
  // 1 · 2막은 평상복으로 사건을 설명한다(피해자 · 가해자를 연기하지 않는다, 10/16 결정): 역할 의상 · 이름표가 없다
  assert.ok(steps.every((s) => !s.look.naero.startsWith('role:') && !s.look.nambul.startsWith('role:')));
  assert.ok(steps.every((s) => s.chips.naero === '' && s.chips.nambul === ''));
  // 3막은 앞 장면의 모습을 이어받는다: 역할 의상이면 의상 이름이 이름표가 되고, 다음 장면도 같은 의상 · 이름표를 유지한다
  assert.equal(trial[0].chips.naero, '검사', '역할 의상이면 의상 이름이 이름표가 된다');
  assert.equal(trial[1].look.naero, trial[0].look.naero, '장면은 앞 장면의 모습을 이어받는다');
  assert.equal(trial[1].chips.nambul, '변호인');
});

test('이름표는 막에서 처음 맡는 배역으로 시작한다(7막 첫 화면부터 "A씨 역", FE-41)', () => {
  const court = flatten(script.acts, ['7']);
  assert.equal(court[0].chips.nambul, 'A씨 역', '대사 전 첫 장면에서 "사건 속 인물"로 뜨지 않는다');
  assert.ok(court.every((s) => s.chips.nambul === 'A씨 역'));
});

test('한 사건 안에서 같은 배역(검사 · 변호인 · 판사 · 사건 속 인물)은 한 캐릭터만 맡는다(10/16 결정)', () => {
  const all = flatten(script.acts, Object.keys(script.acts));
  const byRole = new Map();
  all.forEach((step) => {
    if (!['naero', 'nambul'].includes(step.say) || !step.role || step.role === '안내') return;
    byRole.set(step.role, new Set([...(byRole.get(step.role) ?? []), step.say]));
  });
  byRole.forEach((who, role) => assert.equal(who.size, 1, `${role}를 ${[...who].join(' · ')}가 나눠 맡았다`));
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
