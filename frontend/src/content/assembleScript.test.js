// npm test : 조립기(FE-139) — 서버 블록 + 공통 틀이 무대가 쓰는 대본이 되는가
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assembleScript } from './assembleScript.js';
import { flatten } from '../components/stage.js';
import { getCaseScript } from '../mocks/caseListMockApi.js';

const local = JSON.parse(readFileSync(new URL('./case-1/script.json', import.meta.url)));
const withoutSource = (acts) => JSON.parse(JSON.stringify(acts, (key, value) => (key === 'source' ? undefined : value)));
const response = () => getCaseScript(1);
const swapped = async () => {
  const given = await response();
  const cast = { prosecutor: 'nambul', defense: 'naero' };
  return { ...given, cast, meta: { ...given.meta, cast } };
};

test('기본 배역이면 조립 결과가 사건 1 대본과 막 · 카드 · 증거까지 같다', async () => {
  const script = assembleScript(await response());
  assert.deepEqual(script.acts, withoutSource(local.acts));
  assert.deepEqual(script.cards, local.cards);
  assert.deepEqual(script.evidence, local.evidence);
  assert.deepEqual(script.cast, { prosecutor: 'naero', defense: 'nambul' });
});

test('막 · 카드 이동 값(section · confirm · slots)을 조립기가 채운다', async () => {
  const steps = assembleScript(await response()).acts['2'].steps;
  assert.deepEqual(steps.filter((step) => step.section).map((step) => step.section), [2, 3, 4]);
  assert.deepEqual(steps.filter((step) => step.confirm).map((step) => step.confirm), [2, 3, 4]);
  assert.equal(steps.at(-1).next, '의견 듣기');
  assert.equal(steps.find((step) => step.section === 3).slots.nambul.slot, 30);
});

test('배역이 반대면 법정 막의 화자 · 자리 · 문구가 따라 바뀌고 안내 막은 그대로다', async () => {
  const script = assembleScript(await swapped());
  const trial = script.acts['2'];
  assert.equal(script.cast.prosecutor, 'nambul');
  assert.deepEqual(trial.slots, { nambul: { slot: 10, row: 'front' }, naero: { slot: 50, row: 'front' } });
  assert.equal(trial.steps.find((step) => step.proc === 1).say, 'nambul');
  // 피고인 역으로 서는 쪽(묶음 B)이 따라 바뀐다 — 문장에 이름이 없어도 배역은 cameo · 이름표 · 자리에 실린다
  const stand = trial.steps.find((step) => step.proc === 4);
  assert.equal(stand.cameo, 'naero');
  assert.deepEqual(stand.tags, { naero: 'A씨 역' });
  assert.deepEqual(stand.slots, { naero: { slot: 30, row: 'front' } });
  assert.equal(script.acts['3'].steps[0].say, 'nambul');
  assert.equal(script.acts['5'].steps[0].say, 'nambul', '5막 판사는 묶음 A');
  assert.equal(script.acts['5'].look.nambul, 'role:judge');
  assert.equal(script.acts['1a'].steps[0].say, 'nambul', '1막 안내 순서는 배역과 무관');
  assert.deepEqual(script.acts['6'], withoutSource(local.acts['6']));
});

test('조립 대본은 안내 스텝이 없어 무대가 민감 안내를 한 번만 붙인다', async () => {
  const script = assembleScript(await response());
  assert.equal(flatten(script.acts, ['2']).some((step) => step.system), false);
});

test('마지막 질문 · 답은 issue · admits로 고른다', async () => {
  const given = await response();
  const last = (meta) => assembleScript({ ...given, meta: { ...given.meta, ...meta } }).acts['2'].steps.slice(-5, -3).map((step) => step.text);
  assert.deepEqual(last({ issue: 'admitted', admits: true }), ['공소사실을 인정합니까?', '…네, 인정합니다.']);
  assert.deepEqual(last({ issue: 'denied', admits: false }), ['공소사실을 인정합니까?', '…아닙니다.']);
  assert.deepEqual(last({ issue: 'intent_disputed', admits: true }), ['행위 사실을 인정합니까?', '…네, 인정합니다.']);
  assert.deepEqual(last({ issue: 'defense_claim', admits: false }), ['행위 사실을 인정합니까?', '…아닙니다.']);
});

test('조립할 수 없으면 null이다(준비 중)', async () => {
  const given = await response();
  ['U1', 'U2', 'U3', 'U4'].forEach((unit) => {
    const { [unit]: _dropped, ...blocks } = given.blocks;
    assert.equal(assembleScript({ ...given, blocks }), null, `${unit} 없음`);
  });
  assert.equal(assembleScript({ ...given, meta: { ...given.meta, issue: null } }), null);
  assert.equal(assembleScript({ ...given, meta: { ...given.meta, admits: null } }), null);
  assert.equal(assembleScript({ ...given, blocks: { ...given.blocks, U3: [] } }), null);
  assert.equal(assembleScript({ ...given, blocks: { ...given.blocks, U3: [null] } }), null, 'U3에 null 원소');
  assert.equal(assembleScript({ ...given, blocks: { ...given.blocks, U1: { lines: ['한 줄'] } } }), null);
  assert.equal(assembleScript({ evidence: [], cast: given.cast }), null, '블록 없는 옛 응답');
});

test('증거 카드 문구는 판단 요소(card)이고, 요소가 지워졌으면 대사를 쓴다', async () => {
  const given = await response();
  const evidence = given.evidence.map((line, i) => (i === 0 ? { ...line, card: null } : line));
  const script = assembleScript({ ...given, evidence });
  assert.equal(script.evidence[0].text, given.blocks.U3[0].text);
  assert.equal(script.evidence[1].text, given.evidence[1].card);
});

test('카드의 ul이 문자열 배열이 아니어도 무대가 멈추지 않게 빈 목록으로 둔다', async () => {
  const given = await response();
  const cards = { indict: { ul: '문자열', law: 3 }, plea: { b: ['배열'], ul: [1, '하나', null] }, issues: { p: '쟁점', ul: { 0: '객체' } } };
  const script = assembleScript({ ...given, blocks: { ...given.blocks, U2: { ...given.blocks.U2, cards } } });
  assert.deepEqual(script.cards.indict, { k: '공소사실 · 검사', ul: [], p: [] });
  assert.deepEqual(script.cards.plea.ul, ['하나']);
  assert.deepEqual(script.cards.plea.p, []);
  assert.deepEqual(script.cards.issues, { k: '쟁점 정리', p: ['쟁점'], ul: [] });
});
