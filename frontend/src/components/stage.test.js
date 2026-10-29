// npm test : 무대 대본 진행 로직(모습 · 이름표 이어받기, 대사 건너뛰기 경계, 서버 확인 장면)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ACT_GROUPS, DEFAULT_CAST, castOf, flatten, flipFor, seatFor, sideOf, skipTarget } from './stage.js';

const script = JSON.parse(readFileSync(new URL('../content/case-1/script.json', import.meta.url)));
const steps = flatten(script.acts, ['1a', '1b']);
const trial = flatten(script.acts, ['2']);

test('장면은 앞 장면의 모습 · 이름표를 이어받는다', () => {
  // 1막(기사 1a · 엽서 1b)은 평상복으로 사건을 설명한다(피해자 · 가해자를 연기하지 않는다, 10/16 결정): 역할 의상 · 이름표가 없다
  assert.ok(steps.every((s) => !s.look.naero.startsWith('role:') && !s.look.nambul.startsWith('role:')));
  assert.ok(steps.every((s) => s.chips.naero === '' && s.chips.nambul === ''));
  // 2막 재판은 앞 장면의 모습을 이어받는다: 역할 의상이면 의상 이름이 이름표가 되고, 다음 장면도 같은 의상 · 이름표를 유지한다
  assert.equal(trial[0].chips.naero, '검사', '역할 의상이면 의상 이름이 이름표가 된다');
  assert.equal(trial[1].look.naero, trial[0].look.naero, '장면은 앞 장면의 모습을 이어받는다');
  assert.equal(trial[1].chips.nambul, '변호인');
});

test('이름표는 막에서 처음 맡는 배역으로 시작한다(5막 첫 화면부터 "A씨 역", FE-41)', () => {
  const court = flatten(script.acts, ['5']);
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

test('3막은 최후진술 없이 검사 의견 → 최종변론 → 판결이고, 변호인 의상으로 판결 장면까지 간다(FE-131)', () => {
  const verdict = flatten(script.acts, ['3']);
  assert.deepEqual(script.acts['3'].procedures, ['검사 의견', '최종변론', '판결']);
  assert.ok(!verdict.some((s) => s.role === 'A씨 역'), '"…이상입니다." 한마디를 위해 의상을 바꾸고 가운데로 나오는 장면은 없다(10/28 결정)');
  verdict.forEach((s) => assert.equal(s.look.nambul, 'role:defense', `${s.text ?? s.card} 장면에서 갈아입었다`));
  assert.equal(verdict.at(-1).card, 'verdict');
  assert.deepEqual(verdict.map((s) => s.proc), [0, 1, 2, 2], '절차 번호는 세 단계(0 ~ 2)를 가리킨다');
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
  const lastOfAct1 = steps.findLastIndex((s) => s.actId === '1a');
  assert.equal(skipTarget(steps, 0), lastOfAct1, '1막 기사(1a)에는 보드가 없어 기사 끝까지 간다(엽서로 넘어가지 않는다)');
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

test('대본 키는 화면의 6막 칩(ACT_GROUPS)과 하나씩 맞는다(FE-59)', () => {
  assert.equal(ACT_GROUPS.length, 6);
  assert.deepEqual(ACT_GROUPS.flatMap(({ ids }) => ids).sort(), Object.keys(script.acts).sort());
});

// 묶음 자리(FE-128). 사건 1 대본의 내로 · 남불을 통째로 맞바꾸면 "남불이 판사 · 검사(A)"인 대본이 된다
const SWAPPED_CAST = { prosecutor: 'nambul', defense: 'naero' };
const swapped = JSON.parse(JSON.stringify(script).replaceAll('"naero"', '"@@"').replaceAll('"nambul"', '"naero"').replaceAll('"@@"', '"nambul"'));
const ALL_ACTS = ['1a', '1b', '2', '3', '4', '5', '6'];

test('배역: 대본 cast가 없거나 맞지 않으면 기본 배역(내로 A · 남불 B)', () => {
  assert.deepEqual(castOf(undefined), DEFAULT_CAST);
  assert.deepEqual(castOf({ prosecutor: 'naero', defense: 'naero' }), DEFAULT_CAST);
  assert.deepEqual(castOf({ prosecutor: 'law', defense: 'nambul' }), DEFAULT_CAST);
  assert.deepEqual(castOf(SWAPPED_CAST), SWAPPED_CAST);
  assert.equal(sideOf('naero'), 'left');
  assert.equal(sideOf('naero', SWAPPED_CAST), 'right');
});

test('자리: 역할 의상이면 의상 자리, 피고인 역 · 평상복 · 볼따구는 묶음 쪽', () => {
  assert.equal(seatFor('nambul', 'role:judge', SWAPPED_CAST), 'left');
  assert.equal(seatFor('naero', 'role:defense', SWAPPED_CAST), 'right');
  assert.equal(seatFor('naero', 'role:actor', SWAPPED_CAST), 'right'); // 5막 피고인 역: 이름(내로 = 왼쪽)이 아니라 B 쪽
  assert.equal(seatFor('nambul', 'idle-neutral', SWAPPED_CAST), 'left'); // 4막 평상복
  assert.equal(seatFor('naero', 'cheek-slim:idle-thinking'), 'left'); // 기본 배역이면 지금과 같다
  assert.equal(seatFor('nambul', 'role:actor'), 'right');
});

test('배역을 맞바꾼 대본은 모든 장면에서 자리도 그대로 맞바뀐다(겹침 · 엇갈림 없음)', () => {
  const before = flatten(script.acts, ALL_ACTS);
  const after = flatten(swapped.acts, ALL_ACTS);
  assert.equal(after.length, before.length);
  before.forEach((step, i) => {
    const other = after[i];
    assert.equal(seatFor('naero', other.look.naero, SWAPPED_CAST), seatFor('nambul', step.look.nambul), `${step.actId}막 ${i}: 내로가 원래 남불 자리에`);
    assert.equal(seatFor('nambul', other.look.nambul, SWAPPED_CAST), seatFor('naero', step.look.naero), `${step.actId}막 ${i}: 남불이 원래 내로 자리에`);
  });
});

test('5막 판사 · 피고인과 3 → 4막: 맞바꾼 대본에서도 두 캐릭터가 다른 쪽에 서고 막이 바뀌어도 쪽이 그대로다', () => {
  const after = flatten(swapped.acts, ALL_ACTS);
  after.filter((step) => step.actId === '5').forEach((step) => {
    assert.notEqual(seatFor('naero', step.look.naero, SWAPPED_CAST), seatFor('nambul', step.look.nambul, SWAPPED_CAST));
  });
  const lastOf3 = after.filter((step) => step.actId === '3').at(-1);
  const firstOf4 = after.find((step) => step.actId === '4');
  ['naero', 'nambul'].forEach((who) => {
    assert.equal(seatFor(who, firstOf4.look[who], SWAPPED_CAST), seatFor(who, lastOf3.look[who], SWAPPED_CAST), `${who}: 3 → 4막에서 자리를 바꾸지 않는다`);
  });
});

test('바라보는 방향(FE-128): 서 있는 곳에서 가운데를 보게 뒤집는다 — 원본은 내로 오른쪽 · 남불 왼쪽을 본다', () => {
  assert.equal(flipFor('naero', 'left'), false, '내로는 왼쪽에 서면 원본(오른쪽)대로 가운데를 본다');
  assert.equal(flipFor('naero', 'right'), true);
  assert.equal(flipFor('nambul', 'right'), false, '남불은 오른쪽에 서면 원본(왼쪽)대로 가운데를 본다');
  assert.equal(flipFor('nambul', 'left'), true);
  ['naero', 'nambul'].forEach((who) => assert.equal(flipFor(who, 30), false, `${who}: 가운데(피고인석)는 원본 방향`));
  assert.equal(flipFor('naero', 5), false);
  assert.equal(flipFor('naero', 55), true);
  assert.equal(flipFor('nambul', 5), true);
  assert.equal(flipFor('nambul', 55), false);
  assert.equal(flipFor('naero', undefined), false, '자리를 모르면 뒤집지 않는다');
});

test('사건 1(기본 배역)은 어느 장면에서도 뒤집히지 않는다 (피고인석 제외) — 원래 화면 그대로', () => {
  const seatsOf = (acts, cast) => acts.flatMap((id) => flatten(script.acts, [id])).flatMap((step) => ['naero', 'nambul'].map((who) => ({ who, seat: seatFor(who, step.look[who], cast), stand: step.spots[who] === 'stand' })));
  const ids = Object.keys(script.acts);
  seatsOf(ids, DEFAULT_CAST).filter((s) => !s.stand).forEach((s) => assert.equal(flipFor(s.who, s.seat), false, `기본 배역 ${s.who} ${s.seat}`));
});
