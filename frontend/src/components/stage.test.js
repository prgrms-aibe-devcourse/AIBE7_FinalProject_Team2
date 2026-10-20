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

test('시작 의상과 다른 배역의 대사가 뒤에 나와도, 그 대사 전 장면의 이름표는 의상 이름이다(리뷰 반영)', () => {
  const acts = { x: { look: { naero: 'role:defense', nambul: 'role:actor' }, steps: [
    { system: '막 시작' }, // 대사 전 장면: 변호인 의상은 "변호인", 사건 속 인물 의상은 처음 맡는 "A씨 역"
    { say: 'naero', role: 'A씨 역', text: '…' }, // 변호인 의상으로 서 있는데 대사의 배역이 다름 → 이 장면부터 대본대로 "A씨 역"
    { say: 'nambul', role: 'A씨 역', text: '…' },
  ] } };
  const [before, spoken] = flatten(acts, ['x']);
  assert.equal(before.chips.naero, '변호인', '맞지 않는 "A씨 역"을 대사 전 장면부터 달지 않는다');
  assert.equal(before.chips.nambul, 'A씨 역');
  assert.equal(spoken.chips.naero, 'A씨 역');
});

test('대사 · 이유 없이 의상을 바꾸지 않는다: 3막(대본 5막) 최후진술에서 입은 A씨 역 의상을 판결 장면까지 유지(FE-48)', () => {
  const verdict = flatten(script.acts, ['5']);
  const from = verdict.findIndex((s) => s.say === 'nambul' && s.role === 'A씨 역');
  assert.ok(from >= 0);
  verdict.slice(from).forEach((s) => assert.equal(s.look.nambul, 'role:actor', `${s.text ?? s.card} 장면에서 갈아입었다`));
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

test('자리는 이름("stand") 또는 슬롯 { slot, row }이고, 다음 장면이 이어받는다(FE-39)', () => {
  const acts = { x: { look: { naero: 'role:prosecutor', nambul: 'role:defense' }, steps: [
    { say: 'nambul', spots: { nambul: { slot: 24, row: 'back' } }, text: '…' },
    { say: 'naero', text: '…' }, // 자리를 따로 주지 않으면 앞 장면의 슬롯을 이어받는다
    { say: 'nambul', spots: { nambul: '' }, text: '…' }, // 빈 값이면 기본 자리로 돌아간다
  ] } };
  const [first, second, third] = flatten(acts, ['x']);
  assert.deepEqual(first.spots.nambul, { slot: 24, row: 'back' });
  assert.deepEqual(second.spots.nambul, { slot: 24, row: 'back' });
  assert.equal(third.spots.nambul, '');
});
