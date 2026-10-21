// npm test : 대본 확장 필드(source · slots) 점검 — 퓨샷으로 쓰는 사건 1 대본이 형식을 지키는지
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// scripts.js는 JSON을 import로 읽어 node 테스트에서 못 쓴다 — stage.test.js처럼 파일로 읽는다
const SCRIPTS = { 1: JSON.parse(readFileSync(new URL('./case-1/script.json', import.meta.url))) };

const isSlot = (v) => Number.isInteger(v.slot) && v.slot >= 0 && v.slot <= 60 && ['front', 'back'].includes(v.row);

test('모든 장면에 출처(source)가 있고 종류가 맞다', () => {
  Object.entries(SCRIPTS).forEach(([caseId, script]) => {
    Object.entries(script.acts).forEach(([actId, act]) => act.steps.forEach((step, i) => {
      const at = `사건 ${caseId} ${actId}막 ${i}번`;
      assert.ok(step.source, `${at}: source가 없다`);
      if (step.text) assert.ok(['fixed', 'ai', 'api'].includes(step.source.type), `${at}: 대사 출처 종류`);
      if (step.source.type === 'api') assert.ok(step.source.api && step.source.fills, `${at}: api 출처는 api · fills가 있다`);
      if (step.source.type === 'ai') assert.ok(step.source.basis?.length, `${at}: ai 출처는 basis가 있다`);
      if (step.source.card?.type === 'api') assert.ok(step.source.card.api && step.source.card.fields?.length, `${at}: 카드 api 출처`);
    }));
  });
});

test('대사의 {자리표시자}는 source.fills에 모두 적혀 있다', () => {
  Object.values(SCRIPTS).forEach((script) => Object.values(script.acts).forEach((act) => act.steps.forEach((step) => {
    const used = [...(step.text ?? '').matchAll(/\{([^}]+)\}/g)].map((m) => m[1]);
    used.forEach((name) => assert.ok(step.source.fills?.[name], `${step.text}: {${name}}의 출처(fills)가 없다`));
  })));
});

test('슬롯(slots)은 0~60 칸 · front/back이고, 막마다 내로 · 남불 기본 자리가 있다', () => {
  Object.values(SCRIPTS).forEach((script) => Object.values(script.acts).forEach((act) => {
    ['naero', 'nambul'].forEach((who) => assert.ok(isSlot(act.slots?.[who]), `${act.title}: ${who} 기본 슬롯`));
    act.steps.filter((step) => step.slots).forEach((step) => Object.values(step.slots).forEach((v) => assert.ok(isSlot(v), `${act.title}: 슬롯 값`)));
  }));
});

test('슬롯은 spots와 어긋나지 않는다(stand = 가운데 30, 빈 값 = 막 기본 자리)', () => {
  Object.values(SCRIPTS).forEach((script) => Object.values(script.acts).forEach((act) => act.steps.forEach((step) => {
    Object.entries(step.spots ?? {}).filter(([, v]) => typeof v === 'string').forEach(([who, v]) => {
      const want = v === 'stand' ? 30 : act.slots[who].slot;
      assert.equal(step.slots?.[who]?.slot, want, `${act.title}: ${who} spots "${v}"의 슬롯`);
    });
  })));
});
